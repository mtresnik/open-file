package org.open.file.shared.mongo.localhost

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.api.model.Volume
import org.open.file.shared.mongo.MongoConfig
import java.net.Socket
import org.open.file.utils.FileSystemUtils
import java.io.File

object LocalhostMongoLauncher {

    fun ensureMongoRunning(docker: DockerClient) {
        val containerName = "org.open.file"
        val hostDataPath = File(FileSystemUtils.appHome, "mongo").apply { mkdirs() }.absolutePath

        // Check if container already exists
        val existing = docker.listContainersCmd()
            .withShowAll(true)  // includes stopped containers
            .withNameFilter(listOf(containerName))
            .exec()
            .firstOrNull()

        when {
            existing == null -> {
                println("No container found — creating and starting...")
                createAndStartMongo(docker, containerName, hostDataPath)
            }
            existing.state == "running" -> {
                println("MongoDB already running, skipping launch.")
            }
            else -> {
                println("Container exists but is stopped — restarting...")
                docker.startContainerCmd(existing.id).exec()
            }
        }

        waitForMongo()
    }

    fun createAndStartMongo(docker: DockerClient, name: String, hostPath: String) {
        val dataVolume = Volume("/data/db")
        val bind = Bind(hostPath, dataVolume)

        val container = docker.createContainerCmd("mongo:7.0")
            .withName(name)
            .withVolumes(dataVolume)
            .withHostConfig(
                HostConfig.newHostConfig()
                    .withBinds(bind)
                    .withPortBindings(
                        PortBinding(
                            Ports.Binding.bindPort(27017),
                            ExposedPort.tcp(27017)
                        )
                    )
            )
            .exec()

        docker.startContainerCmd(container.id).exec()
        println("Container started: ${container.id}")
    }

    fun waitForMongo(retries: Int = 20, delayMs: Long = 1000) {
        repeat(retries) { attempt ->
            try {
                Socket("localhost", MongoConfig.mongoPort).close()
                println("MongoDB is ready!")
                return
            } catch (e: Exception) {
                println("Waiting for MongoDB... (${attempt + 1}/$retries)")
                Thread.sleep(delayMs)
            }
        }
        throw RuntimeException("MongoDB did not become ready in time")
    }


}