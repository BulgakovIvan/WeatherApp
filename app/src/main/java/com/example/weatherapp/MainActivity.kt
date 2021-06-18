package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var bi: ActivityMainBinding
    val TAG = "ups"
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    private var mProgressDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bi = ActivityMainBinding.inflate(layoutInflater)
        setContentView(bi.root)

        showCustomProgressDialog()

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (!isLocationEnabled()) {
            Toast.makeText(this, "Your location provider is turned off.", Toast.LENGTH_SHORT).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withContext(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION)
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?)
                    {
                        if (report!!.areAllPermissionsGranted()) {
                            requestLocationData()
                        }

                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "You have denied location permission. " +
                                        "Pleas enable them as it is mandatory for the app to work.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>, token: PermissionToken)
                    {
                        showRationalDialogForPermissions()
                    }
                })
                .onSameThread()
                .check()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest =LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY}

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback, Looper.myLooper()!!)
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
//            Log.e(TAG, "Current Latitude: $latitude")

            val longitude = mLastLocation.longitude
//            Log.e(TAG, "Current Longitude: $longitude")

            getLocationWeatherDetails(latitude, longitude)
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {
        if (Constants.isNetworkAvailable(this)) {

            val retrofit: Retrofit = Retrofit
                .Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()).build()

            val services: WeatherService = retrofit
                .create(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = services.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, getString(R.string.APP_ID)
            )

            listCall.enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(call: Call<WeatherResponse>,
                                        response: Response<WeatherResponse>) {

                    if (response.isSuccessful) {
                        val weatherList: WeatherResponse? = response.body()

                        if (weatherList != null) {
                            setupUI(weatherList)
                        }

                        Log.e(TAG, weatherList.toString())

                    } else {
                        when (response.code()) {
                            400 -> Log.e(TAG, "Bad connection")
                            404 -> Log.e(TAG, "Not found")
                            else -> Log.e(TAG, "Generic Error")
                        }
                    }
                    hideProgressDialog()
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    hideProgressDialog()
                    Log.e(TAG, "Err: ${t.message}")
                }
            })
        } else {
            Toast.makeText(this, "No internet connection available.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCustomProgressDialog() {
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }

    private fun setupUI(weatherList: WeatherResponse) {
        for (i in weatherList.weather.indices) {

            bi.tvMain.text = weatherList.weather[i].main
            bi.tvMainDescription.text = weatherList.weather[i].description
            bi.tvTemp.text = getString(R.string.temp, weatherList.main.temp.toString(), getUnit())
            bi.tvHumidity.text = getString(R.string.humidity, weatherList.main.humidity.toString(), " %")
            bi.tvMin.text = getString(R.string.min, weatherList.main.temp_min.toString())
            bi.tvMax.text = getString(R.string.max, weatherList.main.temp_max.toString())
            bi.tvSpeed.text = weatherList.wind.speed.toString()
            bi.tvName.text = weatherList.name
            bi.tvCountry.text = weatherList.sys.country
            bi.tvSunriseTime.text = unixTime(weatherList.sys.sunrise)
            bi.tvSunsetTime.text = unixTime(weatherList.sys.sunset)

            when (weatherList.weather[i].icon) {
                "01d" -> bi.ivMain.setImageResource(R.drawable.sunny)
                "02d" -> bi.ivMain.setImageResource(R.drawable.cloud)
                "03d" -> bi.ivMain.setImageResource(R.drawable.cloud)
                "04d" -> bi.ivMain.setImageResource(R.drawable.cloud)
                "04n" -> bi.ivMain.setImageResource(R.drawable.cloud)
                "10d" -> bi.ivMain.setImageResource(R.drawable.rain)
                "11d" -> bi.ivMain.setImageResource(R.drawable.storm)
                "13d" -> bi.ivMain.setImageResource(R.drawable.snowflake)
                "01n" -> bi.ivMain.setImageResource(R.drawable.cloud)
                "02n" -> bi.ivMain.setImageResource(R.drawable.cloud)
                "03n" -> bi.ivMain.setImageResource(R.drawable.cloud)
                "10n" -> bi.ivMain.setImageResource(R.drawable.cloud)
                "11n" -> bi.ivMain.setImageResource(R.drawable.rain)
                "13n" -> bi.ivMain.setImageResource(R.drawable.snowflake)
            }
        }
    }

    private fun getUnit(): String {
        val country = Locale.getDefault().country

        return if ("US" == country || "LR" == country || "MM" == country) {
            "°F"
        } else {
            "°C"
        }
    }

    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}