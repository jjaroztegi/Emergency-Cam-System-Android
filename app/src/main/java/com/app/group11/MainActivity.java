package com.app.group11;

import static android.content.ContentValues.TAG;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.DefaultValueFormatter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private EditText angleEditText;
    private ToggleButton modeToggleButton;
    private Button rotateButton;
    private SwipeRefreshLayout swipeRefreshLayout;
    private static final String BASE_URL_ANGLE = "http://192.168.4.1/?angle=";
    private static final String BASE_URL_SENSOR = "http://192.168.4.1/?sensor1";
    private static final String BASE_URL_SENSOR_2 = "http://192.168.4.1/?sensor2";
    private static final String BASE_URL_AUTO = "http://192.168.4.1/?auto";
    private static final String BASE_URL_MANUAL = "http://192.168.4.1/?manual";
    private static final String BASE_URL_CODIF = "http://192.168.4.3/?codif";
    private static final String BASE_URL_VEL1 = "http://192.168.4.1/?vel1";
    private static final String BASE_URL_VEL2 = "http://192.168.4.1/?vel2";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(this::restartApp);

        angleEditText = findViewById(R.id.angleEditText);
        modeToggleButton = findViewById(R.id.modeToggleButton);
        rotateButton = findViewById(R.id.rotateButton);
        Button velocity1Button = findViewById(R.id.velocity1Button);
        Button velocity2Button = findViewById(R.id.velocity2Button);

        rotateButton.setOnClickListener(v -> {
            String angleText = angleEditText.getText().toString().trim();
            if (!angleText.isEmpty()) {
                int angle = Integer.parseInt(angleText);
                sendRotation(angle);
            } else {
                Toast.makeText(MainActivity.this, "Please enter rotation angle", Toast.LENGTH_SHORT).show();
            }
        });

        modeToggleButton.setOnClickListener(v -> {
            boolean autoMode = modeToggleButton.isChecked();
            angleEditText.setEnabled(!autoMode);
            rotateButton.setEnabled(!autoMode);
            if (autoMode) {
                angleEditText.setText("");
                autoRotation();
            } else {
                stopAutoRotation();
            }
        });

        velocity1Button.setOnClickListener(v -> sendVelocityRequest(BASE_URL_VEL1));
        velocity2Button.setOnClickListener(v -> sendVelocityRequest(BASE_URL_VEL2));

        // Start receiving data from Arduino and update the graphs
        LineChart lineChart = findViewById(R.id.lineChart);
        LineChart lineChart1 = findViewById(R.id.lineChart1);
        LineChart lineChart2 = findViewById(R.id.lineChart2);
        DataReceiverTask dataReceiverTask = new DataReceiverTask(lineChart, lineChart1, lineChart2);
        dataReceiverTask.fetchDataFromServer();
    }

    private void restartApp() {
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    private void sendRotation(final int angle) {
        try {
            String encodedAngle = URLEncoder.encode(String.valueOf(angle), "UTF-8");
            String url = BASE_URL_ANGLE + encodedAngle;

            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                try {
                    URL requestUrl = new URL(url);
                    HttpURLConnection connection = (HttpURLConnection) requestUrl.openConnection();
                    connection.setRequestMethod("GET");

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = in.readLine()) != null) {
                            response.append(line);
                        }
                        in.close();
                        String responseBody = response.toString();
                        Log.d("Response", "Response: " + responseBody);

                        if (responseBody.equals("success")) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Rotation command sent successfully",
                                    Toast.LENGTH_SHORT).show());
                        } else {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                    "Unexpected response content: " + responseBody, Toast.LENGTH_SHORT).show());
                        }
                    } else {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                        "Failed to send rotation command. HTTP error code: " + responseCode, Toast.LENGTH_SHORT)
                                .show());
                    }
                    connection.disconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "Failed to send rotation command: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
            executor.shutdown();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private ScheduledExecutorService executor;
    private ExecutorService requestExecutor; // Single executor for handling all requests

    private void autoRotation() {
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> {
            sendAutoRotationRequest();
            executor.shutdown();
        }, 0, TimeUnit.MILLISECONDS);
    }

    private void sendAutoRotationRequest() {
        if (requestExecutor == null || requestExecutor.isShutdown()) {
            requestExecutor = Executors.newFixedThreadPool(5); // Adjust the number of threads as needed
        }
        requestExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(BASE_URL_AUTO);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(2000);
                connection.setReadTimeout(2000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                if (response.toString().equals("success")) {
                    Log.d(TAG, "Auto mode activated successfully");
                } else {
                    Log.e(TAG, "Unexpected response content: " + response);
                }
                connection.disconnect();
            } catch (IOException e) {
                Log.e(TAG, "Failed to activate auto mode: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void stopAutoRotation() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        if (requestExecutor != null && !requestExecutor.isShutdown()) {
            requestExecutor.shutdownNow();
        }
        sendManualModeRequest();
    }

    private void sendManualModeRequest() {
        ExecutorService manualModeExecutor = Executors.newSingleThreadExecutor();
        manualModeExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(BASE_URL_MANUAL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "Switched to manual mode successfully");
                } else {
                    Log.e(TAG, "Failed to switch to manual mode. HTTP error code: " + responseCode);
                }
                connection.disconnect();
            } catch (IOException e) {
                Log.e(TAG, "Failed to switch to manual mode: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
        manualModeExecutor.shutdown();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoRotation();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }

    private void sendVelocityRequest(String baseUrl) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                URL requestUrl = new URL(baseUrl);
                HttpURLConnection connection = (HttpURLConnection) requestUrl.openConnection();
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();
                    String responseBody = response.toString();
                    Log.d("Response", "Response: " + responseBody);

                    if (responseBody.equals("success")) {
                        runOnUiThread(() -> Toast
                                .makeText(MainActivity.this, "Velocity command sent successfully", Toast.LENGTH_SHORT)
                                .show());
                    } else {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                "Unexpected response content: " + responseBody, Toast.LENGTH_SHORT).show());
                    }
                } else {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                    "Failed to send velocity command. HTTP error code: " + responseCode, Toast.LENGTH_SHORT)
                            .show());
                }
                connection.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Failed to send velocity command: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
        executor.shutdown();
    }

    public class DataReceiverTask {
        private final List<Float> rawValues = new ArrayList<>();
        private final List<Float> rawValues2 = new ArrayList<>(); // For sensor 2
        private final List<Float> rawValues3 = new ArrayList<>(); // For encoder
        private final List<Long> timestamps = new ArrayList<>();
        private final List<Long> timestamps2 = new ArrayList<>(); // For sensor 2
        private final List<Long> timestamps3 = new ArrayList<>(); // For encoder
        private final LineChart lineChart;
        private final LineChart lineChart1; // For sensor 2
        private final LineChart lineChart2; // For rotatory encoder

        public DataReceiverTask(LineChart lineChart, LineChart lineChart1, LineChart lineChart2) {
            this.lineChart = lineChart;
            this.lineChart1 = lineChart1;
            this.lineChart2 = lineChart2;
        }

        public void fetchDataFromServer() {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<?> future = executor.submit(() -> {
                try {
                    while (true) {
                        fetchDataFromSensor(BASE_URL_SENSOR, true, false);
                        fetchDataFromSensor(BASE_URL_SENSOR_2, false, false);
                        fetchDataFromSensor(BASE_URL_CODIF, false, true);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            });
            executor.shutdown();
        }

        private void fetchDataFromSensor(String url, boolean isSensor1, boolean isCodif) throws IOException {
            URL sensorUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) sensorUrl.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line = reader.readLine();
            if (line != null) {
                Log.d(TAG, "Response: " + line);
                float sensorReading = Float.parseFloat(line);
                long currentTime = System.currentTimeMillis();
                processData(currentTime, sensorReading, isSensor1, isCodif);
            }
            reader.close();
            connection.disconnect();
        }

        private void processData(long time, float value, boolean isSensor1, boolean isCodif) {
            if (isCodif) {
                rawValues3.add(value);
                timestamps3.add(time);
                if (rawValues3.size() > 5) {
                    rawValues3.remove(0);
                    timestamps3.remove(0);
                }
                updateGraph3();
            } else if (isSensor1) {
                rawValues.add(value);
                timestamps.add(time);
                if (rawValues.size() > 5) {
                    rawValues.remove(0);
                    timestamps.remove(0);
                }
                updateGraph();
            } else {
                rawValues2.add(value);
                timestamps2.add(time);
                if (rawValues2.size() > 5) {
                    rawValues2.remove(0);
                    timestamps2.remove(0);
                }
                updateGraph2();
            }
        }

        private void customizeChart(LineChart chart, boolean isRotatoryEncoder) {
            YAxis yAxis = chart.getAxisLeft();
            if (isRotatoryEncoder) {
                yAxis.setAxisMinimum(-180f);
                yAxis.setAxisMaximum(180f);
            } else {
                yAxis.setAxisMinimum(0f);
                yAxis.setAxisMaximum(20f);
            }

            XAxis xAxis = chart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setDrawAxisLine(true);
            xAxis.setAxisLineColor(Color.BLACK);
            xAxis.setDrawGridLines(false);

            chart.getDescription().setEnabled(false);
            chart.getAxisRight().setEnabled(false);
            chart.getAxisLeft().setDrawGridLines(false);
            chart.getLegend().setEnabled(false);
        }

        private void updateGraph(LineChart chart, ArrayList<Float> rawValues, String dataSetLabel,
                                 boolean isRotatoryEncoder) {
            ArrayList<Entry> entries = new ArrayList<>();
            for (int i = 0; i < rawValues.size(); i++) {
                entries.add(new Entry(i, rawValues.get(i)));
            }

            LineDataSet dataSet = new LineDataSet(entries, dataSetLabel);
            dataSet.setDrawCircles(true);
            dataSet.setCircleColor(Color.BLUE);
            dataSet.setCircleRadius(5f);
            dataSet.setValueTextSize(12f);
            dataSet.setValueTextColor(Color.BLACK);
            dataSet.setDrawValues(true);
            dataSet.setValueFormatter(new DefaultValueFormatter(2));
            dataSet.setColor(Color.BLUE);
            dataSet.setLineWidth(2f);
            dataSet.setDrawFilled(true);

            LineData lineData = new LineData(dataSet);
            chart.setData(lineData);
            chart.invalidate();
            customizeChart(chart, isRotatoryEncoder);
        }

        private void updateGraph() {
            updateGraph(lineChart, (ArrayList<Float>) rawValues, "Averaged Readings", false);
        }

        private void updateGraph2() {
            updateGraph(lineChart1, (ArrayList<Float>) rawValues2, "Sensor 2 Readings", false);
        }

        private void updateGraph3() {
            updateGraph(lineChart2, (ArrayList<Float>) rawValues3, "Rotatory Encoder Readings", true);
        }
    }
}