package nnstu.robots.gpscontrol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

public class MainActivity extends Activity implements SensorEventListener {

	private LocationManager locationManager; // Manager for GPS
	private GoogleMap map; // Google Map object
	private Button start_button; // Button for start navigation
	private LatLng endPoint; // Coordinates of the last point
	private boolean paintedPath; // Path was painted or no
	private ArrayList<LatLng> points; // Array with track point
	private boolean firstLocation; // Found my location or no
	private TextView textHello; // Some text object
    private SensorManager mSensorManager; // device sensor manager for compass
    private TextView showHeading; // TextView for compass
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		paintedPath = false;
		firstLocation = false;
		
		textHello = (TextView) findViewById(R.id.cmd);
        showHeading = (TextView) findViewById(R.id.showHeading);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        
        int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getBaseContext());
 
        if (status!=ConnectionResult.SUCCESS) {
        	int requestCode = 10;
            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(status, this, requestCode);
            dialog.show();
            return;
        }
		
		locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
		
		map = ((MapFragment) getFragmentManager()
                .findFragmentById(R.id.map)).getMap();
		map.getUiSettings().setCompassEnabled(true);
		map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
			@Override
			public void onMapClick(LatLng point) {
				if (paintedPath == true)
					return;
				map.clear();
				endPoint = point;
				map.addMarker(new MarkerOptions().position(point));
			}
		});		
		
		start_button = (Button) findViewById(R.id.start_button);
		//start_button.setEnabled(false);
		start_button.setText(R.string.start_nav);
		start_button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (endPoint == null) {
					Toast toast = Toast.makeText(getApplicationContext(), 
							"Please, select the endpoint!", Toast.LENGTH_SHORT); 
					toast.show(); 
					return;
				}
				if (paintedPath == true) {
					paintedPath = false;
					map.clear();
					endPoint = null;
					start_button.setText(R.string.start_nav);
					return;
				}
				paintedPath = true;
				start_button.setText(R.string.stop_nav);
				
				drawRoute(map.getMyLocation());
			}
		});
	}
	
	private void drawRoute(Location location) {
		// Getting URL to the Google Directions API
    	String url = getDirectionsUrl(new LatLng(location.getLatitude(), 
    			location.getLongitude()), endPoint);				
		
		DownloadTask downloadTask = new DownloadTask();
		
		map.clear();
		map.addMarker(new MarkerOptions().position(endPoint));
		
		// Start downloading json data from Google Directions API
		downloadTask.execute(url);
	}
	
	private String getDirectionsUrl(LatLng origin,LatLng dest){
		
		// Origin of route
		String str_origin = "origin="+origin.latitude+","+origin.longitude;
		
		// Destination of route
		String str_dest = "destination="+dest.latitude+","+dest.longitude;			
					
		// Sensor enabled
		String sensor = "sensor=false";			
					
		// Building the parameters to the web service
		String parameters = str_origin+"&"+str_dest+"&"+sensor;
					
		// Output format
		String output = "json";
		
		// Building the url to the web service
		String url = "https://maps.googleapis.com/maps/api/directions/"+output+"?"+parameters;		
		
		return url;
	}
	
	/** A method to download json data from url */
    private String downloadUrl(String strUrl) throws IOException{
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try{
        	URL url = new URL(strUrl);
        	
        	urlConnection = (HttpURLConnection) url.openConnection();
        	
        	// Connecting to url
        	urlConnection.connect();

                // Reading data from url 
                iStream = urlConnection.getInputStream();

                BufferedReader br = new BufferedReader(new InputStreamReader(iStream));

                StringBuffer sb  = new StringBuffer();

                String line = "";
                while( ( line = br.readLine())  != null){
                        sb.append(line);
                }
                
                data = sb.toString();

                br.close();

        }catch(Exception e){
                Log.d("Exception while downloading url", e.toString());
        }finally{
                iStream.close();
                urlConnection.disconnect();
        }
        return data;
    }
    
    /** A class to download data from Google Directions URL */
	private class DownloadTask extends AsyncTask<String, Void, String>{			
				
		// Downloading data in non-ui thread
		@Override
		protected String doInBackground(String... url) {
				
			// For storing data from web service
			String data = "";
					
			try{
				// Fetching the data from web service
				data = downloadUrl(url[0]);
			}catch(Exception e){
				Log.d("Background Task",e.toString());
			}
			return data;		
		}
		
		// Executes in UI thread, after the execution of
		// doInBackground()
		@Override
		protected void onPostExecute(String result) {			
			super.onPostExecute(result);			
			
			ParserTask parserTask = new ParserTask();
			
			// Invokes the thread for parsing the JSON data
			parserTask.execute(result);
				
		}		
	}
	
	/** A class to parse the Google Directions in JSON format */
    private class ParserTask extends AsyncTask<String, Integer, List<List<HashMap<String,String>>>> {
    	
    	// Parsing the data in non-ui thread    	
		@Override
		protected List<List<HashMap<String, String>>> doInBackground(String... jsonData) {
			
			JSONObject jObject;	
			List<List<HashMap<String, String>>> routes = null;			           
            
            try{
            	jObject = new JSONObject(jsonData[0]);
            	DirectionsParser parser = new DirectionsParser();
            	
            	// Starts parsing data
            	routes = parser.parse(jObject);    
            }catch(Exception e){
            	e.printStackTrace();
            }
            return routes;
		}
		
		// Executes in UI thread, after the parsing process
		@Override
		protected void onPostExecute(List<List<HashMap<String, String>>> result) {
			points = null;
			PolylineOptions lineOptions = null;
			
			// Traversing through all the routes
			for(int i=0;i<result.size();i++){
				points = new ArrayList<LatLng>();
				LatLng position = new LatLng(map.getMyLocation().getLatitude(), 
						map.getMyLocation().getLongitude());
				lineOptions = new PolylineOptions();
				
				// Fetching i-th route
				List<HashMap<String, String>> path = result.get(i);
				
				// Fetching all the points in i-th route
				for(int j=0; j < path.size(); j++){
					HashMap<String,String> point = path.get(j);					
					
					double lat = Double.parseDouble(point.get("lat"));
					double lng = Double.parseDouble(point.get("lng"));
					position = new LatLng(lat, lng);	
					
					points.add(position);
				}
				
				// Adding all the points in the route to LineOptions
				lineOptions.addAll(points);
				lineOptions.width(5);
				lineOptions.color(Color.BLUE);	
				
			}
			
			// Drawing polyline in the Google Map for the i-th route
			map.addPolyline(lineOptions);							
		}			
    }

	@SuppressWarnings("deprecation")
	@Override
	protected void onResume() {
		super.onResume();
		// for the system's orientation sensor registered listeners
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
                SensorManager.SENSOR_DELAY_GAME);
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000 * 10, 10, locationListener);
	    checkEnabled();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		locationManager.removeUpdates(locationListener);
		// to stop the listener and save battery
        mSensorManager.unregisterListener(this);
	}
	
	public void onSensorChanged(SensorEvent event) {
        // get the angle around the z-axis rotated
        float degree = Math.round(event.values[0]);
        Float mapDegree = getGoogleBearing(null);
        if (mapDegree != null)
        {
        	String text = String.valueOf(mapDegree) + " ";
        	int accuracy = 10;
        	float leftBorder = degree + accuracy;
        	float rightBorder = degree - accuracy;
        	leftBorder = (leftBorder >= 360) ? (leftBorder - 360) : leftBorder;
        	rightBorder = (rightBorder < 0) ? (rightBorder + 360) : rightBorder;
        	if (rightBorder > leftBorder)
        	{
        		if (mapDegree < leftBorder || mapDegree > rightBorder)
        			text += "straight";
        		else
        		{
        			float proximityLeft = mapDegree - leftBorder;
        			float proximityRight = rightBorder - mapDegree;
        			if (proximityLeft > proximityRight)
        				text += "left";
        			else
        				text += "right";
        		}
        	}
        	else
        	{
        		if (mapDegree < rightBorder)
        			text += "left";
        		else if (mapDegree > leftBorder)
        			text += "right";
        		else
        			text += "straight";
        	}
        	textHello.setText(text);
        }
 
        showHeading.setText("Heading: " + Float.toString(degree));
    }
 
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
	
	private LocationListener locationListener = new LocationListener() {
		
		@Override
		public void onLocationChanged(Location location) {
			showLocation(location);
		}
		
		@Override
		public void onProviderDisabled(String provider) {
			checkEnabled();
		}
		
		@Override
		public void onProviderEnabled(String provider) {
			checkEnabled();
			showLocation(locationManager.getLastKnownLocation(provider));
		}
		
		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
			/*switch (status) {
				case LocationProvider.OUT_OF_SERVICE:
					//start_button.setEnabled(false);
					break;
				case LocationProvider.TEMPORARILY_UNAVAILABLE:
					//start_button.setEnabled(false);
					break;
				case LocationProvider.AVAILABLE:
					start_button.setEnabled(true);
					break;
			}*/
		}
	};
	
	private Float getGoogleBearing(Location location) {
		if (points == null)
			return null;
		if (location == null)
		{
			location = new Location("");
			location.setLatitude(points.get(0).latitude);
			location.setLongitude(points.get(0).longitude);
		}
		Location nextLoc = new Location("");
		nextLoc.setLatitude(points.get(1).latitude);
		nextLoc.setLongitude(points.get(1).longitude);
		float bearing = location.bearingTo(nextLoc);
		if (bearing < 0) 
			bearing += 360;
		return bearing;
	}
	
	private void showLocation(Location location) {
		if (location == null)
			return;
		
		LatLng my_location = new LatLng(location.getLatitude(), location.getLongitude());
		
        map.setMyLocationEnabled(true);
        if (!firstLocation) {
        	map.moveCamera(CameraUpdateFactory.newLatLngZoom(my_location, 13));
        	firstLocation = true;
        }
        
        if (paintedPath == true) {
        	drawRoute(location);
        }
	}
	
	private void checkEnabled() {
		if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.gps_disabled_text)
			       .setTitle(R.string.gps_disabled_title)
			       .setCancelable(false)
			       .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
					}
				});
			AlertDialog dialog = builder.create();
			dialog.show();
		}
	}
	
}
