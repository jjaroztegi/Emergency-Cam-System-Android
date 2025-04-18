package com.app.group11;

import static android.content.ContentValues.TAG;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private EditText angleEditText;
    private ToggleButton modeToggleButton;
    private View rotateButton;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private TextView sensorVelocityTextView;
    private static final String BASE_URL_ANGLE = "http://192.168.4.1/?angle=";
    private static final String BASE_URL_SENSOR = "http://192.168.4.1/?sensor1";
    private static final String BASE_URL_SENSOR_2 = "http://192.168.4.1/?sensor2";
    private static final String BASE_URL_AUTO = "http://192.168.4.1/?auto";
    private static final String BASE_URL_MANUAL = "http://192.168.4.1/?manual";
    private static final String BASE_URL_CODIF = "http://192.168.4.3/?codif";
    private static final String BASE_URL_VEL1 = "http://192.168.4.1/?vel1";
    private static final String BASE_URL_VEL2 = "http://192.168.4.1/?vel2";
    private static final int CONNECTION_TIMEOUT = 5000; // 5 seconds
    private static final int MAX_ANGLE = 180;
    private static final int MIN_ANGLE = -180;
    private static final int RETRY_DELAY_MS = 1000;
    private static final int MAX_DATA_POINTS = 5;

    private ScheduledExecutorService executor;
    private ExecutorService requestExecutor;
    private DataReceiverTask dataReceiverTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupListeners();
        startDataReceiver();
    }

    private void initializeViews() {
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        angleEditText = findViewById(R.id.angleEditText);
        modeToggleButton = findViewById(R.id.modeToggleButton);
        rotateButton = findViewById(R.id.rotateButton);
        progressBar = findViewById(R.id.progressBar);
        sensorVelocityTextView = findViewById(R.id.sensorVelocityTextView);

        LineChart lineChart = findViewById(R.id.lineChart);
        LineChart lineChart1 = findViewById(R.id.lineChart1);
        LineChart lineChart2 = findViewById(R.id.lineChart2);

        dataReceiverTask = new DataReceiverTask(lineChart, lineChart1, lineChart2);
    }

    private void setupListeners() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        rotateButton.setOnClickListener(v -> {
            String angleText = angleEditText.getText().toString().trim();
            if (!angleText.isEmpty()) {
                try {
                    int angle = Integer.parseInt(angleText);
                    if (angle >= MIN_ANGLE && angle <= MAX_ANGLE) {
                        showProgress(true);
                        sendRotation(angle);
                    } else {
                        Toast.makeText(MainActivity.this,
                                "Angle must be between " + MIN_ANGLE + " and " + MAX_ANGLE,
                                Toast.LENGTH_SHORT).show();
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(MainActivity.this, "Please enter a valid number",
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, "Please enter rotation angle",
                        Toast.LENGTH_SHORT).show();
            }
        });

        modeToggleButton.setOnClickListener(v -> {
            boolean autoMode = modeToggleButton.isChecked();
            angleEditText.setEnabled(!autoMode);
            rotateButton.setEnabled(!autoMode);
            if (autoMode) {
                angleEditText.setText("");
                showProgress(true);
                autoRotation();
            } else {
                stopAutoRotation();
            }
        });

        findViewById(R.id.velocity1Button).setOnClickListener(v -> {
            showProgress(true);
            sendVelocityRequest(BASE_URL_VEL1);
        });

        findViewById(R.id.velocity2Button).setOnClickListener(v -> {
            showProgress(true);
            sendVelocityRequest(BASE_URL_VEL2);
        });
    }

    private void showProgress(boolean show) {
        runOnUiThread(() -> progressBar.setVisibility(show ? View.VISIBLE : View.GONE));
    }

    private void updateConnectionStatus(boolean connected) {
        runOnUiThread(() -> {
            sensorVelocityTextView
                    .setTextColor(connected ? ContextCompat.getColor(this, android.R.color.holo_green_dark)
                            : ContextCompat.getColor(this, android.R.color.holo_red_dark));
            sensorVelocityTextView
                    .setText(connected ? "Connected - Reading velocity sensor" : "Disconnected - Check connection");
        });
    }

    private void startDataReceiver() {
        if (dataReceiverTask != null) {
            dataReceiverTask.fetchDataFromServer();
        }
    }

    private void sendRotation(final int angle) {
        try {
            String encodedAngle = URLEncoder.encode(String.valueOf(angle), "UTF-8");
            String url = BASE_URL_ANGLE + encodedAngle;

            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                HttpURLConnection connection = null;
                try {
                    URL requestUrl = new URL(url);
                    connection = (HttpURLConnection) requestUrl.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(CONNECTION_TIMEOUT);
                    connection.setReadTimeout(CONNECTION_TIMEOUT);

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
                        Log.d(TAG, "Response: " + responseBody);

                        if (responseBody.equals("success")) {
                            updateConnectionStatus(true);
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this, "Rotation command sent successfully",
                                        Toast.LENGTH_SHORT).show();
                                showProgress(false);
                            });
                        } else {
                            handleError("Unexpected response: " + responseBody);
                        }
                    } else {
                        handleError("HTTP error code: " + responseCode);
                    }
                } catch (IOException e) {
                    handleError("Connection error: " + e.getMessage());
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            });
            executor.shutdown();
        } catch (UnsupportedEncodingException e) {
            handleError("Encoding error: " + e.getMessage());
        }
    }

    private void handleError(String error) {
        Log.e(TAG, error);
        updateConnectionStatus(false);
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
            showProgress(false);
        });
    }

    private void autoRotation() {
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> {
            sendAutoRotationRequest();
            executor.shutdown();
        }, 0, TimeUnit.MILLISECONDS);
    }

    private void sendAutoRotationRequest() {
        if (requestExecutor == null || requestExecutor.isShutdown()) {
            requestExecutor = Executors.newFixedThreadPool(5);
        }
        requestExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(BASE_URL_AUTO);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECTION_TIMEOUT);
                connection.setReadTimeout(CONNECTION_TIMEOUT);

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
                connection.setConnectTimeout(CONNECTION_TIMEOUT);
                connection.setReadTimeout(CONNECTION_TIMEOUT);

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "Switched to manual mode successfully");
                } else {
                    Log.e(TAG, "Failed to switch to manual mode. HTTP error code: " + responseCode);
                }
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
        if (requestExecutor != null && !requestExecutor.isShutdown()) {
            requestExecutor.shutdownNow();
        }
        if (dataReceiverTask != null) {
            dataReceiverTask.stop();
        }
    }

    private void sendVelocityRequest(String baseUrl) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL requestUrl = new URL(baseUrl);
                connection = (HttpURLConnection) requestUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECTION_TIMEOUT);
                connection.setReadTimeout(CONNECTION_TIMEOUT);

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
                    Log.d(TAG, "Response: " + responseBody);

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
            } catch (IOException e) {
                Log.e(TAG, "Failed to send velocity command: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Failed to send velocity command: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                showProgress(false);
            }
        });
        executor.shutdown();
    }

    public class DataReceiverTask {
        private final List<Float> rawValues = new ArrayList<>();
        private final List<Float> rawValues2 = new ArrayList<>();
        private final List<Float> rawValues3 = new ArrayList<>();
        private final LineChart lineChart;
        private final LineChart lineChart1;
        private final LineChart lineChart2;
        private volatile boolean isRunning = true;
        private ExecutorService executor;
        private static final long POLL_INTERVAL_MS = 100; // 100ms polling interval

        public DataReceiverTask(LineChart lineChart, LineChart lineChart1, LineChart lineChart2) {
            this.lineChart = lineChart;
            this.lineChart1 = lineChart1;
            this.lineChart2 = lineChart2;
            customizeCharts();
        }

        private void customizeCharts() {
            customizeChart(lineChart, false);
            customizeChart(lineChart1, false);
            customizeChart(lineChart2, true);
        }

        public void fetchDataFromServer() {
            executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> {
                while (isRunning) {
                    try {
                        fetchDataFromSensor(BASE_URL_SENSOR, true, false);
                        fetchDataFromSensor(BASE_URL_SENSOR_2, false, false);
                        fetchDataFromSensor(BASE_URL_CODIF, false, true);
                        updateConnectionStatus(true);

                        // Use a proper polling interval instead of Thread.sleep
                        if (isRunning) {
                            try {
                                TimeUnit.MILLISECONDS.sleep(POLL_INTERVAL_MS);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error fetching data: " + e.getMessage());
                        updateConnectionStatus(false);
                        if (!isRunning)
                            break;

                        // Use a proper polling interval for retry
                        try {
                            TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                return null;
            });
        }

        private void fetchDataFromSensor(String url, boolean isSensor1, boolean isEncoder) throws IOException {
            HttpURLConnection connection = null;
            try {
                URL sensorUrl = new URL(url);
                connection = (HttpURLConnection) sensorUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECTION_TIMEOUT);
                connection.setReadTimeout(CONNECTION_TIMEOUT);

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line = reader.readLine();
                if (line != null) {
                    Log.d(TAG, "Response: " + line);
                    float sensorReading = Float.parseFloat(line);
                    processData(sensorReading, isSensor1, isEncoder);
                }
                reader.close();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        private void processData(float value, boolean isSensor1, boolean isEncoder) {
            if (isEncoder) {
                rawValues3.add(value);
                if (rawValues3.size() > MAX_DATA_POINTS) {
                    rawValues3.remove(0);
                }
                updateGraph3();
            } else if (isSensor1) {
                rawValues.add(value);
                if (rawValues.size() > MAX_DATA_POINTS) {
                    rawValues.remove(0);
                }
                updateGraph();
            } else {
                rawValues2.add(value);
                if (rawValues2.size() > MAX_DATA_POINTS) {
                    rawValues2.remove(0);
                }
                updateGraph2();
            }
        }

        private void customizeChart(LineChart chart, boolean isEncoder) {
            YAxis yAxis = chart.getAxisLeft();
            if (isEncoder) {
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

        private void updateGraph(LineChart chart, List<Float> values, String dataSetLabel,
                boolean isEncoder) {
            ArrayList<Entry> entries = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) {
                entries.add(new Entry(i, values.get(i)));
            }

            LineDataSet dataSet = new LineDataSet(entries, dataSetLabel);
            dataSet.setDrawCircles(true);
            dataSet.setCircleColor(isEncoder ? Color.RED : Color.BLUE);
            dataSet.setCircleRadius(5f);
            dataSet.setValueTextSize(12f);
            dataSet.setValueTextColor(Color.BLACK);
            dataSet.setDrawValues(true);
            dataSet.setValueFormatter(new DefaultValueFormatter(2));
            dataSet.setColor(isEncoder ? Color.RED : Color.BLUE);
            dataSet.setLineWidth(2f);
            dataSet.setDrawFilled(true);

            LineData lineData = new LineData(dataSet);
            chart.setData(lineData);
            chart.invalidate();
        }

        private void updateGraph() {
            updateGraph(lineChart, rawValues, "Averaged Readings", false);
        }

        private void updateGraph2() {
            updateGraph(lineChart1, rawValues2, "Sensor 2 Readings", false);
        }

        private void updateGraph3() {
            updateGraph(lineChart2, rawValues3, "Encoder Readings", true);
        }

        public void stop() {
            isRunning = false;
            if (executor != null && !executor.isShutdown()) {
                executor.shutdownNow();
            }
        }
    }
}