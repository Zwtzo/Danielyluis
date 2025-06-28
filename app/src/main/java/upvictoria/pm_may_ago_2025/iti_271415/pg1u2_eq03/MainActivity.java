package upvictoria.pm_may_ago_2025.iti_271415.pg1u2_eq03;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.*;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.android.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA = 100;
    private static final int REQUEST_IMAGE_CAPTURE = 101;

    TextView firebaseText, statusText;
    String textoFirebase = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        firebaseText = findViewById(R.id.firebaseText);
        statusText = findViewById(R.id.statusText);
        Button capturarBtn = findViewById(R.id.capturarBtn);

        // Cargar OpenCV
        if (OpenCVLoader.initDebug()) {
            statusText.setText("OpenCV cargado correctamente ✅");
        } else {
            statusText.setText("Error al cargar OpenCV ❌");
        }

        // Obtener texto aleatorio desde Firebase
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("parrafos");
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> textos = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String contenido = child.child("contenido").getValue(String.class);
                    if (contenido != null) {
                        textos.add(contenido);
                    }
                }

                if (!textos.isEmpty()) {
                    int index = new Random().nextInt(textos.size());
                    textoFirebase = textos.get(index);
                    firebaseText.setText("Escribe: " + textoFirebase);
                } else {
                    firebaseText.setText("No hay textos en Firebase");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                firebaseText.setText("Error al cargar texto: " + error.getMessage());
            }
        });

        // Botón de cámara
        capturarBtn.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        REQUEST_CAMERA);
            } else {
                abrirCamara();
            }
        });
    }

    private void abrirCamara() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
        } else {
            Toast.makeText(this, "No se pudo abrir la cámara", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK && data != null) {
            Bitmap foto = (Bitmap) data.getExtras().get("data");
            Toast.makeText(this, "Imagen capturada. Procesando...", Toast.LENGTH_SHORT).show();

            analizarEscritura(foto); // 👈 Aquí llamas a tu método de análisis
        }
    }


    private void analizarEscritura(Bitmap bitmap) {
        Mat mat = new Mat();
        Utils.bitmapToMat(bitmap, mat);

        // 1. Convertir a escala de grises
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY);

        // 2. Suavizado y umbralización
        Imgproc.GaussianBlur(mat, mat, new Size(5, 5), 0);
        Imgproc.threshold(mat, mat, 0, 255, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU);

        // 3. Buscar contornos
        List<MatOfPoint> contornos = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mat, contornos, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        int circulares = 0, rectos = 0;

        for (MatOfPoint contorno : contornos) {
            double area = Imgproc.contourArea(contorno);
            if (area < 100) continue; // Ignorar ruido

            MatOfPoint2f contorno2f = new MatOfPoint2f(contorno.toArray());
            double perimetro = Imgproc.arcLength(contorno2f, true);

            if (perimetro == 0) continue;

            double circularidad = 4 * Math.PI * area / (perimetro * perimetro);

            if (circularidad > 0.7) {
                circulares++;
            } else if (circularidad < 0.4) {
                rectos++;
            }
        }

        int total = circulares + rectos;
        int score = total > 0 ? (int) Math.round(((circulares + rectos) / (double) total) * 10) : 0;

        runOnUiThread(() -> {
            Toast.makeText(this, "Calificación estimada: " + score + "/10", Toast.LENGTH_LONG).show();
        });
    }

    // Si el usuario da permiso a la cámara
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            abrirCamara();
        } else {
            Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show();
        }
    }
}
