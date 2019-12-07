package dev.cccm5.movecraft

import com.okkero.skedule.SynchronizationContext
import com.okkero.skedule.schedule
import dev.cccm5.movecraft.craft.Craft
import dev.cccm5.movecraft.craft.PlayerCraft
import dev.cccm5.movecraft.hitbox.SolidHitBox
import dev.cccm5.movecraft.util.Direction
import dev.cccm5.movecraft.util.GridVector
import dev.cccm5.movecraft.util.Rotation
import dev.cccm5.movecraft.util.getBlockAt
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.Rotatable
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue

object CraftStateManager {
    private data class TransferData(val origin: GridVector, val translated: GridVector, val material: Material, val data: BlockData)
    private data class Task(val craft: Craft, val translation: GridVector = GridVector(0, 0, 0), val rotation: Rotation = Rotation.NONE){
        operator fun plus(vector: GridVector): Task {
            return Task(craft, translation + vector, rotation)
        }
        operator fun plus(rotation: Rotation): Task {
            return Task(craft, translation, rotation + this.rotation)
        }
    }
    private val crafts = mutableListOf<Craft>()
    private val pilots = mutableSetOf<UUID>()
    private val tasks = PriorityBlockingQueue<Task>()
    private val queuedCrafts = ConcurrentHashMap<Craft, Task>()
    fun generatePlayerCraft(position: GridVector, player: Player){
        pilots.add(player.uniqueId)
        //TODO: Add search for craft bounds

        crafts.add(PlayerCraft(player, SolidHitBox(0, 0, 0, 0, 0, 0), Direction.NORTH, player.world))
    }

    /**
     * Adds a request to translate the craft during the next possible game tick. If a request already exists, it is
     * merged. Craft's are not guaranteed to be moved the next tick if server performance is inadequate.
     *
     * A craft must have been generated by the craft manager to successfully be translated
     */
    fun enqueueTranslation(craft: PlayerCraft, vector: GridVector = GridVector(0, 0, 0), rotation: Rotation = Rotation.NONE){
        queuedCrafts[craft] = if(queuedCrafts.containsKey(craft)){
            //TODO: Fix race condition if craft is de-queued after if conditional
            queuedCrafts[craft]!! + vector + rotation
        } else {
            Task(craft, vector, rotation)
        }



    }

    init{
        Movecraft.logger.info("Starting state manager")
        val scheduler = Bukkit.getScheduler()
        scheduler.schedule(Movecraft.plugin, SynchronizationContext.ASYNC){
            // Repeat execution every tick
            repeating(1)

            val startTime = System.nanoTime()
            // Limit execution to a maximum of one standard tick to attempt to better synchronize with the game-thread
            val timeout = 5_000_000L
            while(startTime + timeout < System.nanoTime() && !tasks.isEmpty()){
                switchContext(SynchronizationContext.ASYNC)
                val (craft, translation, rotation)= tasks.take()
                val world = craft.world
                val newHitBox = craft.hitBox.translate(translation, rotation)
                val taskData = mutableListOf<TransferData>()
                switchContext(SynchronizationContext.SYNC)
                if(newHitBox.any { world.getBlockAt(it).type != Material.AIR }){
                    continue
                }
                for(vector in craft.hitBox){
                    val rotated = (vector + translation).rotate(rotation, craft.hitBox.midpoint)
                    val blockData = world.getBlockAt(vector).blockData
                    if(blockData is Rotatable){
                        TODO("Implement handling of rotated block data")
                    }
                    taskData.add(TransferData(vector, rotated, world.getBlockAt(vector).type, blockData))
                }
                for((_, result, type, data) in taskData){
                    val block = world.getBlockAt(result)
                    block.blockData = data
                    block.type = type
                }

                for(vector in craft.hitBox - newHitBox){
                    world.getBlockAt(vector).type = Material.AIR
                }

            }
        }
    }
}