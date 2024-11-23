package net.shibadogs.prcm.process

import net.shibadogs.prcm.server.logList
import net.shibadogs.prcm.server.memoryUsageList
import net.shibadogs.prcm.server.nodeList
import net.shibadogs.prcm.server.processlist
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Instant

fun run(processInfo: Builder) : Int {
    if (processlist.getOrElse(processInfo.status.id) { null } != null || !processInfo.status.exit) {
        return 0
    }
    if (logList.getOrElse(processInfo.status.id) { null } == null) {
        logList[processInfo.status.id] = StringBuilder()
    }
    val processBuilder = ProcessBuilder(processInfo.node.node, processInfo.node.filepath, *processInfo.node.args)
    processBuilder.redirectErrorStream(false)
    val exists: Boolean = processInfo.node.workdir?.exists() ?: false
    if (exists) {
        processBuilder.directory(processInfo.node.workdir)
    }
    try {
        while (processInfo.status.errCount <= 10) {
            if (processInfo.status.rootExit) {
                processInfo.processInfo.pid = -1
                processInfo.status.endTime = Instant.now().epochSecond
                processInfo.status.exit = true
                processInfo.status.exitCode = 130
                processInfo.status.rootExit = false
                break
            }
            processInfo.status.startTime = Instant.now().epochSecond
            processInfo.status.endTime = -1
            processInfo.status.exit = false
            var line: String?
            val process = processBuilder.start()
            nodeList[processInfo.status.id] = processInfo
            processlist[processInfo.status.id] = process
            memoryUsageList[processInfo.status.id] = listOf()
            processInfo.processInfo.pid = PID(process)
            val standardOutputReader = BufferedReader(InputStreamReader(process.inputStream, "UTF-8"))
            while (standardOutputReader.readLine().also { line = it } != null) {
                logList[processInfo.status.id]?.append(line)?.append("\n")
            }
            val errorOutputReader = BufferedReader(InputStreamReader(process.errorStream, "UTF-8"))
            while (errorOutputReader.readLine().also { line = it } != null) {
                logList[processInfo.status.id]?.append(line)?.append("\n")
            }
            memoryUsageList[processInfo.status.id]?.plus(MemoryUsage(processInfo.processInfo.pid))
            val exit = process.waitFor()
            processInfo.status.endTime = Instant.now().epochSecond
            processInfo.status.exit = true
            processInfo.status.exitCode = exit
            if (processInfo.status.exitCode != 0) {
                processInfo.status.errCount += 1
            }
            processInfo.status.restartCount += 1
        }
        memoryUsageList.remove(processInfo.status.id)
        processlist.remove(processInfo.status.id)
        nodeList.remove(processInfo.status.id)
    } catch (e: Exception) {
        logList[processInfo.status.id]?.append(e.printStackTrace())?.append("\n")
    }
    return processInfo.status.exitCode
}

fun stop(processInfo: Builder) : Boolean{
    if (!processInfo.status.exit) {
        processInfo.status.rootExit = true
        processlist[processInfo.status.id]?.destroyForcibly()
        memoryUsageList.remove(processInfo.status.id)
        processlist.remove(processInfo.status.id)
        nodeList.remove(processInfo.status.id)
        return true
    }
    return false
}