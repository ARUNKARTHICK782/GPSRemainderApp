package com.example.gpsremindermapbox

import android.util.Log
import com.mapbox.navigation.base.route.NavigationRoute
import kotlin.math.roundToInt

object Algorithm {
    private var TAG: String = "Algorithm Says"

    private var distanceMatrix: Array<Array<NavigationRoute?>> = Array(100) { Array(100) { null } }
    private var duration = 0.0
    private var distance = 0.0
    private var priorityCompromiseTable: IntArray = IntArray(5) { i -> ((i * -2) + 10) * 1000 }

    var currentDirecting = -1
    var noOfNodes: Int = 0
    var orderedRoutes: MutableList<NavigationRoute> = mutableListOf()
    var isAdded = true
    var isDirecting = false

    fun addNode(nodeDistance: MutableList<NavigationRoute?>) {
        for (v in 0 until nodeDistance.size) {
            distanceMatrix[v][noOfNodes] = nodeDistance[v]
            distanceMatrix[noOfNodes][v] = nodeDistance[v]
        }

        noOfNodes++
        isAdded = true
        printMatrix()
    }

    fun getDirectingIndex(): Int {
        return (++currentDirecting) % (getSize() - 1)
    }

    fun getDistance(): String {
        return (distance / 1000).roundToInt().toString() + "km"
    }

    fun getDuration(): String {
        val hours = duration / 3600
        val minutes = (duration % 3600) / 60
        val seconds = duration % 60

        return String.format("%02d:%02d:%02d", hours.toInt(), minutes.toInt(), seconds.toInt())
    }

    fun clear() {
        currentDirecting = -1
        orderedRoutes.clear()
        isDirecting = false
        noOfNodes = 0
        distance = 0.0
        duration = 0.0
    }

    fun printMatrix() {
        Log.i(TAG, getSize().toString())

        for (u in 0 until getSize()) {
            for (v in 0 until getSize()) {
                if (distanceMatrix[u][v] != null) {
                    Log.i(
                        TAG,
                        "$u - $v : ${distanceMatrix[u][v]?.directionsRoute?.distance().toString()}"
                    )
                } else Log.i(TAG, "$u - $v : NULL")
            }
        }
    }


    private fun getSize(): Int {
        return noOfNodes
    }

    fun computeGraph(currentIndex: Int, nodes: MutableList<NodePoint>) {
        var index = currentIndex
        val visited = Array(getSize()) { false }
        val newNodes = mutableListOf<NodePoint>()
        var checker = (noOfNodes * (noOfNodes - 1)) / 2

        orderedRoutes.clear()

        for (i in 0 until getSize() - 1) {
            //Short Distance Tracker
            var minDistance = Double.MAX_VALUE
            lateinit var shortRoute: NavigationRoute
            var mIndex = -1
            var minDistancePriority = -1

            //High Priority Tracker
            var highPriority = 6
            lateinit var highPRoute: NavigationRoute
            var pIndex = -1

            Log.i(TAG, "Current Index: $index Position: $i")

            // Flags Modification
            newNodes.add(nodes[index])
            visited[index] = true
            checker -= index
            val persistIndex = index

            for (v in 0 until getSize()) {
                val it = distanceMatrix[persistIndex][v]

                if (it != null && !visited[v]) {
                    if (it.directionsRoute.distance() < minDistance) {
                        minDistance = it.directionsRoute.distance()
                        shortRoute = it
                        mIndex = v
                        minDistancePriority = nodes[v].priority
                    }
                    if (nodes[v].priority < highPriority) {
                        highPriority = nodes[v].priority
                        highPRoute = it
                        pIndex = v
                    }
                }
            }

            //If True -> Priority Route Else Short Route
            if (
                minDistancePriority != highPriority &&
                minDistance >= highPRoute.directionsRoute.distance() - priorityCompromiseTable[highPriority - 1]
            ) {
                duration += highPRoute.directionsRoute.duration()
                distance += highPRoute.directionsRoute.distance()
                orderedRoutes.add(highPRoute)
                index = pIndex
            } else {
                duration += shortRoute.directionsRoute.duration()
                distance += shortRoute.directionsRoute.distance()
                orderedRoutes.add(shortRoute)
                index = mIndex
            }

        }

        newNodes.add(nodes[checker])
        nodes.clear()
        nodes.addAll(newNodes)
        isDirecting = true
    }
}