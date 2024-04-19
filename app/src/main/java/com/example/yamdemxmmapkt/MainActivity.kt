package com.example.yamdemxmmapkt


import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Geometry
import com.yandex.mapkit.geometry.LinearRing
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polygon
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.layers.ObjectEvent
import com.yandex.mapkit.map.CameraListener

import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.CameraUpdateReason
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.map.MapWindow
import com.yandex.mapkit.map.VisibleRegionUtils
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.road_events_layer.StyleProvider
import com.yandex.mapkit.search.Response
import com.yandex.mapkit.search.SearchFactory
import com.yandex.mapkit.search.SearchManager
import com.yandex.mapkit.search.SearchManagerType
import com.yandex.mapkit.search.SearchOptions
import com.yandex.mapkit.search.SearchType
import com.yandex.mapkit.search.Session
import com.yandex.mapkit.search.Session.SearchListener
import com.yandex.mapkit.search.SuggestSession
import com.yandex.mapkit.uri.UriObjectMetadata
import com.yandex.mapkit.user_location.UserLocationObjectListener
import com.yandex.mapkit.user_location.UserLocationView
import com.yandex.runtime.image.ImageProvider
import com.yandex.runtime.Error


class MainActivity : AppCompatActivity() {
    lateinit var searchManager: SearchManager
    lateinit var searchOptions: SearchOptions
    lateinit var searchSessionListener: SearchListener
    lateinit var map: Map

    // Просто переменные координат
    companion object {
        private val POINT = Point(55.751280, 37.629720)
        private val POINT2 = Point(59.935493, 30.327392)
        private val POSITION = CameraPosition(POINT2, 17.0f, 150.0f, 30.0f)
        private val POSITION2 = CameraPosition(POINT, 17.0f, 150.0f, 30.0f)
    }
    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Должно быть обязательно перед setContentView
        MapKitFactory.initialize(this)
        setContentView(R.layout.activity_main)
        mapView = findViewById(R.id.mapview)
        val editText = findViewById<EditText>(R.id.edit_query);
        map = mapView.mapWindow.map

        // Передвижение камеры
         map.move(POSITION)
        // Передвижение с анимацией
        // map.move(POSITION, Animation(Animation.Type.SMOOTH, 10f), null)

        requestLocationPermission()
        // Добавление слоя пробок
        val mapKit = MapKitFactory.getInstance();
        mapKit.createTrafficLayer(mapView.mapWindow).apply {
            isTrafficVisible = true
        };
        // Отображает текущее местоположение на карте (требуются наличие координат в эмуляторе)
        mapKit.createUserLocationLayer(mapView.mapWindow).apply {
            isVisible = true
        };


        // Перевод картинки в Bitmap
        val imageProvider = ImageProvider.fromBitmap(this.getBitmapFromVectorDrawable(R.drawable.test_pin))
        val imageProvider2 = ImageProvider.fromBitmap(this.getBitmapFromVectorDrawable(R.drawable.test_pin2))

        // Создание метки на координате (Метка в Москве)
        // https://yandex.ru/dev/mapkit/doc/ru/android/generated/tutorials/map_objects
        // TODO Не декларативно не работает
        val placemark1 = mapView.map.mapObjects.addPlacemark(
            POINT,
            ImageProvider.fromBitmap(this.getBitmapFromVectorDrawable(R.drawable.test_pin)));
        placemark1.addTapListener(placemarkTapListener)


        // Создание нескольких меток
        // https://yandex.ru/dev/mapkit/doc/ru/android/generated/tutorials/map_objects#collections
        val pinsCollection = mapView.map.mapObjects.addCollection()
        val points = listOf(
            Point(59.935493, 30.327392),
            Point(59.938185, 30.32808),
            Point(59.937376, 30.33621),
            Point(59.934517, 30.335059),
        )
        points.forEach { point ->
            pinsCollection.addPlacemark().apply {
                geometry = point
                setIcon(imageProvider)
            }

        }

        // Выделение области
        // https://yandex.ru/dev/mapkit/doc/ru/android/generated/tutorials/map_objects#geometries
        val polygon = Polygon(LinearRing(points), emptyList())
        map.mapObjects.addPolygon(polygon)

        // Создание линий
        // https://yandex.ru/dev/mapkit/doc/ru/android/generated/tutorials/map_objects#polylines
        val polypoints = listOf(
            POINT,
            Point(59.938185, 30.32808),
            Point(59.937376, 30.33621),
            POINT2,
        )
        val polylineObject = map.mapObjects.addPolyline(Polyline(polypoints))
        polylineObject.apply {
            strokeWidth = 5f
            setStrokeColor(ContextCompat.getColor(this@MainActivity, R.color.grey))
            outlineWidth = 1f
            outlineColor = ContextCompat.getColor(this@MainActivity, R.color.black)
        }

        // Поиск
        // https://yandex.ru/dev/mapkit/doc/ru/android/generated/tutorials/map_search
       searchManager = SearchFactory.getInstance().createSearchManager(SearchManagerType.COMBINED)
        // Обязательная настройка параметров поиска по организациям
        searchOptions = SearchOptions().apply {
            searchTypes = SearchType.BIZ.value
            resultPageSize = 32
            geometry
        }
        searchManager.createSuggestSession()
               searchSessionListener = object : SearchListener {//SearchSessionListener
                   override fun onSearchResponse(response: Response) {
                   // TODO По умолчанию в начале сам что-то ищет, но не понятно что и можно ли это отключить
                       val geoObjects = response.collection.children.mapNotNull { it.obj }
                   val mapObject = map.mapObjects
                   var POINT_ = POINT2
                   geoObjects.forEach { geoObject ->
                       // Вывод списка найденных элементов в логи
                       Log.e("GEO" + geoObjects.indexOf(geoObject).toString(), geoObject.name.toString());
                        geoObject.geometry.forEach {
                            Log.e("for" + geoObjects.indexOf(geoObject).toString(), it.point?.latitude.toString() + " "+ it.point?.longitude.toString());
                        }
                       Log.e("--" ,"--");
                       // TODO Список результатов, не по расстоянию от текущего места
                       POINT_ = Point(geoObject.geometry[0].point!!.latitude, geoObject.geometry[0].point!!.longitude)
                       // Добавление меток на все результаты
                       mapObject.addPlacemark(POINT_, imageProvider2)
                   }
                   // Переход на результат
                   map.move(CameraPosition(POINT_, 17.0f, 150.0f, 30.0f))
                   }
                   override fun onSearchError(error: Error) {
                       Log.e("ERROR!", error.toString())
                   }
               }
        editText.setOnClickListener {
            searchQuery(editText.text.toString())
        }
    }
    // Метод поиска
    fun searchQuery(query:String){
        searchManager.submit(
            query,
            VisibleRegionUtils.toPolygon(map.visibleRegion),
            searchOptions,
            searchSessionListener,
        )
    }
    // Перевод картинки в Bitmap
    // TODO Да будет так
    fun Context.getBitmapFromVectorDrawable(drawableId: Int): Bitmap? {
        var drawable = ContextCompat.getDrawable(this, drawableId) ?: return null

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            drawable = DrawableCompat.wrap(drawable).mutate()
        }

        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888) ?: return null
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }

    // Метод нажатия на метку
    private val placemarkTapListener = MapObjectTapListener { _, point ->
        Toast.makeText(
            this@MainActivity,
            "Tapped the point (${point.longitude}, ${point.latitude})",
            Toast.LENGTH_SHORT
        ).show()
        true
    }

    // Запрос разрешения на геолокацию
    private fun requestLocationPermission(){
        if ( ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        )
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 0)
        return

    }
    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        mapView.onStart()
    }
    override fun onStop() {
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }
}