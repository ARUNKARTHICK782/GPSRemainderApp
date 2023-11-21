package com.example.gpsremindermapbox

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.Transformation
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.content.res.AppCompatResources
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gpsremindermapbox.listener.MyMapBoxNavigationObserver
import com.example.gpsremindermapbox.listener.SelectedSearchChangeEvent
import com.example.gpsremindermapbox.listener.StartingPointChangeEvent
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.AnnotationPlugin
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotation
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions
import com.mapbox.search.ResponseInfo
import com.mapbox.search.ReverseGeoOptions
import com.mapbox.search.SearchCallback
import com.mapbox.search.SearchEngine
import com.mapbox.search.SearchEngineSettings
import com.mapbox.search.autocomplete.PlaceAutocomplete
import com.mapbox.search.autocomplete.PlaceAutocompleteSuggestion
import com.mapbox.search.common.AsyncOperationTask
import com.mapbox.search.result.SearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar


class MainActivity : ComponentActivity() {
    private val mainTag: String = "MainActivity Says"

    //UI Utils
    private lateinit var mapView: MapView
    private lateinit var myLocation: Location
    private lateinit var annotationApi: AnnotationPlugin
    private lateinit var pointAnnotationManager: PointAnnotationManager
    private lateinit var mapObserver: MyMapBoxNavigationObserver
    private lateinit var nodesAdapter: NodesAdapter
    private lateinit var searchAdapter: SearchAdapter
    private lateinit var latestPoint: Point
    private lateinit var clearRoute: NavigationRoute
    private lateinit var editTexts: Array<EditText?>
    private lateinit var icons: Array<ImageView?>
    private lateinit var sliderCircle: CardView
    private lateinit var sliderBody: RelativeLayout
    private lateinit var placeAutocomplete: PlaceAutocomplete
    private lateinit var showBottomButton: ImageButton
    private lateinit var myLocationButton: ImageButton
    private lateinit var searchEngine: SearchEngine
    private lateinit var searchRequestTask: AsyncOperationTask
    private lateinit var tempAddress: String

    //Callbacks
    private lateinit var backPressedCallBack: OnBackPressedCallback
    private val routesReqCallback = object : NavigationRouterCallback {
        override fun onRoutesReady(
            routes: List<NavigationRoute>,
            routerOrigin: RouterOrigin
        ) {
            Log.i(mainTag, "In Route Callback")

            if (!hasClearRoute) {
                clearRoute = routes[0]
                hasClearRoute = true
                Log.i(mainTag, "Got Clear Route")
            } else {
                Log.i(mainTag, "Distance Route ${routes[0].directionsRoute.distance()}")
                newRowForMatrix.add(routes[0])

                if (newRowForMatrix.size + 1 == nodes.size) {
                    newRowForMatrix.add(null)
                    Algorithm.addNode(newRowForMatrix)
                    newRowForMatrix.clear()
                    newRowForMatrixIndex = 0
                } else
                    getRouteBetweenTwoPoints(nodes[++newRowForMatrixIndex].point, latestPoint)
            }

        }

        override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
            if (!hasClearRoute) {
                Log.i(mainTag, "Cancelled")

                val last = nodes.size - 1
                removePin(nodes.removeAt(last).pin)
                nodesAdapter.notifyItemRemoved(last)
                Algorithm.isAdded = true
            } else Log.i(mainTag, "Cancelled to Get Clear Route")
        }

        override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
            if (!hasClearRoute) {
                Log.i(mainTag, "Failed")

                Toast.makeText(ref, "No Route Found, Try Again", Toast.LENGTH_LONG).show()

                val last = nodes.size - 1
                removePin(nodes.removeAt(last).pin)
                nodesAdapter.notifyItemRemoved(last)
                Algorithm.isAdded = true
            } else Log.i(mainTag, "Failed to Get Clear Route")

        }
    }
    private val searchCallback = object : SearchCallback {
        override fun onError(e: Exception) {
            Log.i(mainTag, "Error reverse geocoding results")
        }

        override fun onResults(
            results: List<SearchResult>,
            responseInfo: ResponseInfo
        ) {
            if (results.isEmpty()) {
                Log.i(mainTag, "No reverse geocoding results")
            } else {
                tempAddress = results.first().name
                if(tempFlag)
                    setLocationText(null, results.first().name)
            }
        }
    }

    //Func Utils
    private var tempPin: PointAnnotation? = null
    private var tempFlag = false
    private var job: Job? = null
    private var widthPriorView = 0
    private var myCircleChip: CircleAnnotation? = null
    private val nodes = mutableListOf<NodePoint>()
    private val searches = mutableListOf<PlaceAutocompleteSuggestion>()
    private var ref = this
    private var newRowForMatrix: MutableList<NavigationRoute?> = mutableListOf()
    private var newRowForMatrixIndex = 0
    private var hasClearRoute = false
    private var isOpen: Boolean = false
    private var isTouchedLayout = false
    private var isTouchedCircle = false


    @SuppressLint("MissingPermission", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Setting Search Dialog
        val searchDialog: LinearLayout = findViewById(R.id.searchDialog)
        val translateValue =
            Resources.getSystem().displayMetrics.heightPixels.toFloat() - dpToPx(this, 150)
        searchDialog.translationY = translateValue

        //Permission Check
        checkPermission(
            listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )

        createNotifyChannel()


        //Init MapBox Objects
        mapView = findViewById(R.id.mapView)
        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS)

        annotationApi = mapView.annotations
        pointAnnotationManager = annotationApi.createPointAnnotationManager()

        placeAutocomplete = PlaceAutocomplete.create(
            accessToken = getString(R.string.mapbox_access_token),
        )

        searchEngine = SearchEngine.createSearchEngineWithBuiltInDataProviders(
            SearchEngineSettings(getString(R.string.mapbox_access_token))
        )

        //Init MapBox Navigation
        if (!MapboxNavigationApp.isSetup()) {
            MapboxNavigationApp.setup {
                NavigationOptions.Builder(this)
                    .accessToken(getString(R.string.mapbox_access_token))
                    .build()
            }.attach(lifecycleOwner = this)
            MapboxNavigationApp.current()?.startTripSession()

            mapObserver = MyMapBoxNavigationObserver()
            MapboxNavigationApp.registerObserver(mapObserver)
        }


        //Init Layout
        editTexts = Array(4) { null }
        editTexts[0] = findViewById(R.id.locationEdit)
        editTexts[1] = findViewById(R.id.nameEdit)
        editTexts[2] = findViewById(R.id.dateEdit)
        editTexts[3] = findViewById(R.id.searchEdit)

        icons = Array(3) { null }
        icons[0] = findViewById(R.id.locationEditIcon)
        icons[1] = findViewById(R.id.nameEditIcon)
        icons[2] = findViewById(R.id.dateEditIcon)

        val nodesList: RecyclerView = findViewById(R.id.nodesList)
        val searchList: RecyclerView = findViewById(R.id.searchList)
        showBottomButton = findViewById(R.id.showBottomButton)

        icons[0]?.setOnClickListener {
            if(tempFlag) setLocationText(latestPoint, null)
            else {
                if(tempAddress != "")
                    setLocationText(null, tempAddress)
                else
                    setLocationText(null, "Loading...")
            }
        }

        //Search Listener
        editTexts[3]?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun afterTextChanged(p0: Editable?) {
                if (p0.toString() != "")
                    autoCompleteToSearch(p0.toString())
                else {
                    val size = searches.size
                    searches.clear()
                    searchAdapter.notifyItemRangeRemoved(0, size)

                    myLocationButton.setImageResource(R.drawable.baseline_my_location_24)
                }
            }
        })
        editTexts[3]?.setOnFocusChangeListener { _, b ->
            if (b) searchList.visibility = View.VISIBLE
            else searchList.visibility = View.GONE
        }

        //Touch Listener for Slider
        sliderBody = findViewById(R.id.sliderBody)
        sliderCircle = findViewById(R.id.sliderCircle)

        findViewById<View>(R.id.prior1).doOnLayout {
            widthPriorView = it.measuredWidth
            setSliderX(widthPriorView, -0.2, sliderCircle)
        }

        sliderBody.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isTouchedCircle) {
                        isTouchedLayout = true
                    }
                }


                MotionEvent.ACTION_MOVE -> {
                    if (isTouchedLayout) {
                        if (event.x - 100 > 0)
                            sliderCircle.translationX = event.x - 100

                        adjustSlider(
                            sliderCircle.translationX.toInt(),
                            widthPriorView,
                            sliderCircle,
                            false
                        )
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (isTouchedCircle)
                        isTouchedLayout = false

                    adjustSlider(
                        sliderCircle.translationX.toInt(),
                        widthPriorView,
                        sliderCircle,
                        true
                    )

                }
            }

            true
        }
        sliderCircle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> isTouchedCircle = true
                MotionEvent.ACTION_UP -> isTouchedCircle = false
            }
            false
        }

        //Setting Listeners
        findViewById<ImageView>(R.id.dateEditIcon).setOnClickListener {
            pickDateTime()
        }

        setLocationListener()

        myLocationButton = findViewById(R.id.my_loc_btn)
        myLocationButton.setOnClickListener {
            if (tempPin == null) {
                flyToCameraPosition(lat = myLocation.latitude, long = myLocation.longitude)
                editTexts[3]?.clearFocus()
            } else if (Algorithm.isAdded && !Algorithm.isDirecting)
                setupDetailsOfTask(null, tempAddress)
            else
                Toast.makeText(this, "Clear you current Navigation", Toast.LENGTH_LONG).show()
        }

        val myDirectionButton: ImageButton = findViewById(R.id.direct)
        myDirectionButton.setOnClickListener {
            if (Algorithm.isAdded && hasClearRoute) {
                if (!Algorithm.isDirecting) {

                    myDirectionButton.setImageResource(R.drawable.baseline_directions_24)
                    Algorithm.printMatrix()
                    if (nodesAdapter.curIndex == -1) {
                        nodesAdapter.curIndex = 0
                        changeTypeOfNode(nodes[0], true)
                    }

                    Log.i(mainTag, nodesAdapter.curIndex.toString())
                    Algorithm.computeGraph(nodesAdapter.curIndex, nodes)
                    nodes.forEach {
                        Log.i(mainTag, it.toString())
                    }
                    nodesAdapter.notifyAllChange()
                    setDurAndDis(true)
                }
                mapView.getMapboxMap().getStyle()
                    ?.let { it1 ->
                        drawRouteOnMap(
                            mutableListOf(Algorithm.orderedRoutes[Algorithm.getDirectingIndex()]),
                            it1
                        )
                    }
            }
            else if(!hasClearRoute) Toast.makeText(this, "MapBox server is down, Try Again Later", Toast.LENGTH_LONG).show()

        }

        val clearButton: TextView = findViewById(R.id.clearButton)
        clearButton.setOnClickListener {
            Log.i(mainTag, "Clearing")

            Algorithm.clear()
            nodesAdapter.clear(nodes.size)

            nodes.forEach {
                removePin(it.pin)
            }
            nodes.clear()
            setDurAndDis(false)

            mapView.getMapboxMap().getStyle()
                ?.let { it1 ->
                    drawRouteOnMap(mutableListOf(clearRoute), it1)
                }
        }

        showBottomButton.setOnClickListener {
            if (!Algorithm.isDirecting) {
                val translateAnimation: Animation = object : Animation() {
                    override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                        super.applyTransformation(interpolatedTime, t)
                        searchDialog.translationY =
                            (if (isOpen) 1 - interpolatedTime else interpolatedTime) * translateValue
                    }
                }
                translateAnimation.duration = 1000
                searchDialog.startAnimation(translateAnimation)
                isOpen = !isOpen

                Handler(Looper.myLooper()!!).postDelayed({
                    if (!isOpen) resetAddTaskPage()
                    showBottomButton.rotation = if (isOpen) 180F else 0F
                }, 1000)
            }
        }

        val myAddTaskButton: Button = findViewById(R.id.addTaskButton)
        myAddTaskButton.setOnClickListener {
            val pin = setPin(latestPoint, isStart = false)

            setPinLogic(
                latestPoint,
                pin,
                findViewById<TextView>(R.id.priorText).text.toString(),
                findViewById<EditText>(R.id.nameEdit).text.toString(),
                findViewById<EditText>(R.id.dateEdit).text.toString()
            )

            //Resetting
            showBottomButton.performClick()
            editTexts[3]?.setText("")
            editTexts[3]?.clearFocus()
            myLocationButton.setImageResource(R.drawable.baseline_my_location_24)
            tempFlag = false
            tempPin = null
            tempAddress = ""
        }

        mapView.getMapboxMap().addOnMapClickListener(onMapClickListener = {
            Log.i(mainTag, "Setting Pin ")
            if (Algorithm.isAdded) {
                if (!Algorithm.isDirecting) {
                    latestPoint = Point.fromLngLat(it.longitude(), it.latitude())
                    Log.i(mainTag, "Gettin Loca Details")
                    getDetailsFromPoint(longitude = latestPoint.longitude(), latitude = latestPoint.latitude())

                    setupDetailsOfTask(latestPoint, null)

                    true
                } else {
                    Toast.makeText(this, "Clear you current Navigation", Toast.LENGTH_LONG).show()
                    false
                }
            } else false
        })

        //BackPress
        backPressedCallBack =  object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.i(mainTag, "handleOnBackPressed: Exit" + nodes.size + " <- size")

                if (Algorithm.isDirecting) {
                    Algorithm.isDirecting = false
                    Algorithm.currentDirecting = -1

                    Log.i(mainTag, "${nodesAdapter.curIndex}")

                    changeTypeOfNode(nodes[nodesAdapter.curIndex], false)

                    nodes.sortBy { it.pos }
                    setDurAndDis(false)

                    mapView.getMapboxMap().getStyle()
                        ?.let { it1 ->
                            drawRouteOnMap(mutableListOf(clearRoute), it1)
                        }
                    nodesAdapter.curIndex = -1
                    myDirectionButton.setImageResource(R.drawable.baseline_directions_bike_24)

                    nodesAdapter.notifyAllChange()
                }
                else if (isOpen) showBottomButton.performClick()
                else tempPin?.let {
                    removePin(it)
                    editTexts[3]?.setText("")
                    editTexts[3]?.clearFocus()
                }
                    ?: if (nodes.size != 0) {
                        val last = nodes.size - 1
                        removePin(nodes.removeAt(last).pin)
                        nodesAdapter.notifyItemRemoved(last)
                        Algorithm.noOfNodes--
                    } else {
                        this.isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }

            }
        }
        onBackPressedDispatcher.addCallback(this, backPressedCallBack)

        //Setting Up Recycler View - Nodes
        nodesAdapter = NodesAdapter(nodes, this, object : StartingPointChangeEvent {
            override fun onStartPointChange(oldStart: Int, newStart: Int) {
                if (oldStart != -1) changeTypeOfNode(nodes[oldStart], false)
                if (newStart != -1) {
                    changeTypeOfNode(nodes[newStart], true)
//                    setupDetailsOfTask(nodes[newStart].point, nodes[newStart].)
                }
            }
        })

        nodesList.adapter = nodesAdapter
        nodesList.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        //Setting Up Recycler View - Search
        searchAdapter = SearchAdapter(searches, this, object : SelectedSearchChangeEvent {
            override fun onSelectedSearchChange(suggestion: PlaceAutocompleteSuggestion) {
                latestPoint = Point.fromLngLat(
                    suggestion.coordinate.longitude(),
                    suggestion.coordinate.latitude()
                )
                tempPin = setPin(latestPoint = latestPoint, false)
                tempAddress = suggestion.name

                editTexts[3]?.setText(suggestion.name)

                flyToCameraPosition(
                    suggestion.coordinate.latitude(),
                    suggestion.coordinate.longitude()
                )

                editTexts[3]?.clearFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(editTexts[3]?.windowToken, 0)

                myLocationButton.setImageResource(R.drawable.baseline_add_location_alt_24)
            }
        })

        searchList.adapter = searchAdapter
        searchList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        //Getting Clear Route
        Handler(Looper.getMainLooper()).postDelayed({
            getRouteBetweenTwoPoints(
                Point.fromLngLat(-68.708943, 18.598624),
                Point.fromLngLat(-68.708354, 18.598574)
            )
        }, 2000)
    }

    override fun onResume() {
        super.onResume()
        backPressedCallBack.isEnabled = true
    }

    //UI Utils
    private fun changeTypeOfNode(node: NodePoint, isStart: Boolean) {
        Log.i(mainTag, node.pin.id.toString())

        removePin(node.pin)
        node.pin = setPin(node.point, isStart)

        Log.i(mainTag, node.pin.id.toString())
    }

    private fun resetAddTaskPage() {
        for (v in 0 until 3){
            editTexts[v]?.setText("")
            editTexts[v]?.clearFocus()
        }

        setSliderX(widthPriorView, -0.2, sliderCircle)
        setPriorityText("5", ref.getColor(R.color.prior_5))
    }

    private fun setDurAndDis(isVisible: Boolean) {
        findViewById<RelativeLayout>(R.id.disAndDurContainer).visibility =
            if (isVisible) View.VISIBLE else View.GONE
        "Total Distance: ${Algorithm.getDistance()}\n\nTotal Duration: ${Algorithm.getDuration()}".also {
            findViewById<TextView>(
                R.id.disAndDur
            ).text = it
        }
    }

    private fun setPriorityText(priority: String, color: Int) {
        findViewById<TextView>(R.id.priorText).text = priority
        findViewById<TextView>(R.id.priorText).setTextColor(color)
    }

    private fun setSliderX(value: Int, mul: Double, sliderCircle: CardView) {
        sliderCircle.translationX = ((value.times(0.5)).plus(value * mul)).toFloat()
    }

    private fun adjustSlider(translateX: Int, width: Int, sliderCircle: CardView, isUp: Boolean) {
        when (translateX) {
            in width * -1..width -> {
                setPriorityText("5", ref.getColor(R.color.prior_5))
                if (isUp) setSliderX(width, -0.2, sliderCircle)
            }

            in width..width * 2 -> {
                setPriorityText("4", ref.getColor(R.color.prior_4))
                if (isUp) setSliderX(width, 0.9, sliderCircle)
            }

            in width * 2..width * 3 -> {
                setPriorityText("3", ref.getColor(R.color.prior_3))
                if (isUp) setSliderX(width, 1.95, sliderCircle)

            }

            in width * 3..width * 4 -> {
                setPriorityText("2", ref.getColor(R.color.prior_2))
                if (isUp) setSliderX(width, 3.0, sliderCircle)

            }

            in width * 4..width * 7 -> {
                setPriorityText("1", ref.getColor(R.color.prior_1))
                if (isUp) setSliderX(width, 4.05, sliderCircle)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDetailsOfTask(latestPoint: Point?, address: String?) {
        if (!isOpen) {
            showBottomButton.performClick()
        }

        if(latestPoint != null) setLocationText(latestPoint, null)
        else setLocationText(null, address)

        editTexts[1]?.setText(getString(R.string.nameText, nodes.size + 1))
        editTexts[2]?.setText(
            SimpleDateFormat.getDateTimeInstance().format(Calendar.getInstance().time)
        )
    }

    private fun setLocationText(latestPoint: Point?, address: String?){
        if (latestPoint!=null) {
            editTexts[0]?.setText(
                getString(
                    R.string.locationText,
                    latestPoint.longitude().toFloat(),
                    latestPoint.latitude().toFloat()
                )
            )
            tempFlag = false
        } else {
            val modifiedAddress = if(address?.length!! > 30) address.substring(0, 30) + "..." else address

            Log.i(mainTag, modifiedAddress)

            editTexts[0]?.setText(
                getString(
                    R.string.locationTextAlt,
                    modifiedAddress
                )
            )
            tempFlag = true
        }
    }

    private fun pickDateTime() {
        val currentDateTime = Calendar.getInstance()
        val startYear = currentDateTime.get(Calendar.YEAR)
        val startMonth = currentDateTime.get(Calendar.MONTH)
        val startDay = currentDateTime.get(Calendar.DAY_OF_MONTH)
        val startHour = currentDateTime.get(Calendar.HOUR_OF_DAY)
        val startMinute = currentDateTime.get(Calendar.MINUTE)


        DatePickerDialog(this, { _, year, month, day ->
            TimePickerDialog(this, { _, hour, minute ->
                val pickedDateTime = Calendar.getInstance()
                pickedDateTime.set(year, month, day, hour, minute)

                editTexts[2]?.setText(
                    SimpleDateFormat.getDateTimeInstance().format(pickedDateTime.time)
                )

            }, startHour, startMinute, false).show()
        }, startYear, startMonth, startDay).show()
    }

    //Service Methods - Will be Separated
    //TODO: Create A Service
    @SuppressLint("MissingPermission")
    private fun setLocationListener() {
        val circleAnnotationManager = annotationApi.createCircleAnnotationManager()

        val listener = LocationListener {
            myLocation = it
            Log.i(mainTag, "Lat => ${myLocation.latitude} Long => ${myLocation.longitude}")

            val circleAnnotationOptions: CircleAnnotationOptions = CircleAnnotationOptions()
                .withPoint(Point.fromLngLat(myLocation.longitude, myLocation.latitude))
                .withCircleRadius(8.0)
                .withCircleColor("#ee4e8b")
                .withCircleStrokeWidth(2.0)
                .withCircleStrokeColor("#ffffff")

            if (myCircleChip != null) circleAnnotationManager.delete(myCircleChip!!)

            myCircleChip = circleAnnotationManager.create(circleAnnotationOptions)

            val indexes = lookForRemainderFunction(it)
            if (indexes.size > 0) {
                indexes.forEach { index ->
                    sendNotificationAboutTheTask(nodes[index])
                }
            }
        }
        val locationManager: LocationManager =
            (this.getSystemService(LOCATION_SERVICE) as LocationManager)

        Log.i(mainTag, "Setting Location Listener")
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (!gpsEnabled) {
            Log.i(mainTag, "GPS Not Enabled")
            val settingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(settingsIntent)
        }
        locationManager.requestLocationUpdates(
            LocationManager.NETWORK_PROVIDER,
            10000,
            10F,
            listener
        )
    }

    private fun lookForRemainderFunction(curr_loc: Location): MutableList<Int> {
        val reminderIndices = mutableListOf<Int>()
        nodes.forEach {
            val itLoc = Location("Task " + it.pos)

            itLoc.latitude = it.point.latitude()
            itLoc.longitude = it.point.longitude()
            if (calculateDistanceFromTwoPoints(itLoc, curr_loc) < 500.0) {
                reminderIndices.add(it.pos)
            }
        }

        return reminderIndices
    }

    private fun calculateDistanceFromTwoPoints(src: Location, dest: Location): Double {
        val distance = src.distanceTo(dest)
        Log.i(mainTag, distance.toString())

        return distance.toDouble()
    }

    @SuppressLint("MissingPermission")
    private fun sendNotificationAboutTheTask(node: NodePoint) {
        val builder = NotificationCompat.Builder(this, MyApplication.channelID)
            .setSmallIcon(R.drawable.baseline_directions_bike_24)
            .setContentTitle("Reminder From GPS Navigation")
            .setContentText("You are nearby ${node.location} to do ${node.name}, You can go there, if your want")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        Log.i(mainTag, node.toString())

        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(1, builder.build())
    }


    private fun createNotifyChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MyApplication.channelID,
                "GPS Navigation",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "To notify about GPS task nearby"
            val notificationManager = getSystemService(
                NotificationManager::class.java
            )
            notificationManager.createNotificationChannel(channel)

            Log.i("Says","Channel Created")
        }
    }

    private fun checkPermission(permissions: List<String>) {
        permissions.forEach {
            if (ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission Not Granted", Toast.LENGTH_LONG).show()
                return
            }
        }
    }

    private fun getDetailsFromPoint(longitude: Double, latitude: Double) {
        val options = ReverseGeoOptions(
            center = Point.fromLngLat(longitude, latitude),
            limit = 1
        )
        searchRequestTask = searchEngine.search(options, searchCallback)
    }

    //Map Methods
    private fun autoCompleteToSearch(searchText: String) {
        lifecycleScope.launch {
            job?.cancel()

            job = launch {
                delay(500)
                Log.i(mainTag, "Started Searching")
                val response = placeAutocomplete.suggestions(
                    query = searchText,
                )

                if (response.isValue) {
                    val suggestions = requireNotNull(response.value)

                    searches.clear()
                    if (suggestions.size >= 5) searches.addAll(suggestions.subList(0, 5))
                    else searches.addAll(suggestions)
                    searchAdapter.notifyItemRangeChanged(0, 5)
                } else {
                    Log.i("SearchApiExample", "Place Autocomplete error", response.error)
                }
            }

        }
    }

    private fun flyToCameraPosition(lat: Double, long: Double) {
        val cameraCenterCoordinate = Point.fromLngLat(long, lat)
        val cameraOptions = CameraOptions.Builder()
            .center(cameraCenterCoordinate)
            .zoom(15.5)
            .bearing(-17.6)
            .build()

        val animationOptions = MapAnimationOptions.Builder().duration(5000).build()

        mapView.getMapboxMap()
            .flyTo(cameraOptions = cameraOptions, animationOptions = animationOptions)
    }

    private fun setPin(latestPoint: Point, isStart: Boolean): PointAnnotation {
        tempPin?.let {
            removePin(it)
            tempPin = null
        }
        val pointAnnotationOptions: PointAnnotationOptions =
            if (!isStart)
                PointAnnotationOptions()
                    .withPoint(latestPoint)
                    .withIconImage(
                        AppCompatResources.getDrawable(this, R.drawable.pin)!!.toBitmap(100, 100)
                    )
            else
                PointAnnotationOptions()
                    .withPoint(latestPoint)
                    .withIconImage(
                        AppCompatResources.getDrawable(this, R.drawable.start_pin)!!
                            .toBitmap(100, 100)
                    )



        return pointAnnotationManager.create(pointAnnotationOptions)

    }

    private fun setPinLogic(
        latestPoint: Point,
        pin: PointAnnotation,
        priority: String,
        name: String,
        date: String
    ) {
        if (nodes.size == 0) {
            newRowForMatrix.add(null)
            Algorithm.addNode(newRowForMatrix)
            newRowForMatrix.clear()
        } else {
            Log.i(mainTag, "Getting Routes")
            Algorithm.isAdded = false

            getRouteBetweenTwoPoints(nodes[0].point, latestPoint)
        }

        nodes.add(
            NodePoint(
                point = latestPoint,
                pos = nodes.size,
                pin = pin,
                priority = priority.toInt(),
                name = name,
                date = date,
                location = "Crittenden Lane"
            )
        )
        nodes.forEach { Log.i(mainTag, it.toString()) }
        nodesAdapter.notifyAdded()


    }

    private fun removePin(pin: PointAnnotation) {
        Log.i(mainTag, "Removed Pin")
        pointAnnotationManager.delete(pin)
    }

    private fun getRouteBetweenTwoPoints(originPoint: Point, destinationPoint: Point) {
        Log.i(mainTag, "Started Getting")

        mapObserver.newMBN?.requestRoutes(
            RouteOptions.builder()
                .profile(DirectionsCriteria.AMENITY_TYPE_GAS_STATION)
                .applyDefaultNavigationOptions()
                .coordinatesList(listOf(destinationPoint, originPoint))
                .build(),
            routesReqCallback
        )
    }

    private fun drawRouteOnMap(routes: List<NavigationRoute>, routeStyle: Style) {
        routes.forEach {
            Log.i(mainTag, "Distance Route ${it.directionsRoute.distance()}")
        }

        val routeLineOptions = MapboxRouteLineOptions.Builder(this).build()
        val routeLineApi = MapboxRouteLineApi(routeLineOptions)
        val routeLineView = MapboxRouteLineView(routeLineOptions)

        routeLineApi.setNavigationRoutes(routes) { value ->
            Log.i(mainTag, "Calculating and Drawing")
            routeLineView.renderRouteDrawData(routeStyle, value)
        }
    }

    //Utils
    private fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
