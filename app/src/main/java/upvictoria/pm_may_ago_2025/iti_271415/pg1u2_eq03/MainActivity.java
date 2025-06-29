package upvictoria.pm_may_ago_2025.iti_271415.pg1u2_eq03;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

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
    private static final String TAG = "VALIDATION_APP";

    TextView firebaseText, statusText;
    ImageView imageView;
    String textoFirebase = "";

    TextRecognizer textRecognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        firebaseText = findViewById(R.id.firebaseText);
        statusText = findViewById(R.id.statusText);
        imageView = findViewById(R.id.imageView);
        Button capturarBtn = findViewById(R.id.capturarBtn);

        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        if (OpenCVLoader.initDebug()) {
            Log.d(TAG, "OpenCV cargado correctamente.");
        } else {
            Log.e(TAG, "Error al cargar OpenCV.");
        }

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
        startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK && data != null) {
            Bitmap foto = (Bitmap) data.getExtras().get("data");
            imageView.setImageBitmap(foto);
            statusText.setText("Estado: Leyendo texto...");
            iniciarValidacion(foto);
        }
    }

    private void iniciarValidacion(Bitmap bitmap) {
        if (bitmap == null) {
            Toast.makeText(this, "No se pudo obtener la imagen.", Toast.LENGTH_SHORT).show();
            return;
        }

        InputImage inputImage = InputImage.fromBitmap(bitmap, 0);
        textRecognizer.process(inputImage)
                .addOnSuccessListener(visionText -> {
                    String textoExtraido = visionText.getText();
                    CompararTexto(textoExtraido, bitmap);
                })
                .addOnFailureListener(e -> {
                    statusText.setText("Estado: Error al leer texto.");
                    Toast.makeText(MainActivity.this, "Error al procesar la imagen: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void CompararTexto(String textoExtraido, Bitmap bitmap) {
        String textoNormalizadoFirebase = normalizarTexto(textoFirebase);
        String textoNormalizadoExtraido = normalizarTexto(textoExtraido);

        Log.d(TAG, "Firebase Normalizado: " + textoNormalizadoFirebase);
        Log.d(TAG, "Extraído Normalizado: " + textoNormalizadoExtraido);

        if (textoNormalizadoFirebase.equals(textoNormalizadoExtraido)) {
            statusText.setText("Estado: Contenido CORRECTO ✅. Analizando caligrafía...");
            Toast.makeText(this, "¡Texto correcto! Ahora calificando la escritura...", Toast.LENGTH_SHORT).show();
            analizarEscritura(bitmap);
        } else {
            statusText.setText("Estado: Contenido INCORRECTO ❌.");
            Toast.makeText(this, "La escritura no coincide con la frase.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Analiza la escritura y asigna una calificación de 1 a 10.
     */
    private void analizarEscritura(Bitmap bitmap) {
        Mat mat = new Mat();
        Utils.bitmapToMat(bitmap, mat);

        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.GaussianBlur(mat, mat, new Size(5, 5), 0);
        Imgproc.threshold(mat, mat, 0, 255, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU);

        List<MatOfPoint> contornos = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mat, contornos, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        int totalTrazos = 0;
        for (MatOfPoint contorno : contornos) {
            // Filtramos contornos muy pequeños para ignorar el ruido
            if (Imgproc.contourArea(contorno) > 50) {
                totalTrazos++;
            }
        }

        // --- LÓGICA DE CALIFICACIÓN ---
        int calificacion = 0;
        int largoFrase = textoFirebase.replaceAll("\\s", "").length(); // Largo sin espacios

        if (largoFrase == 0) {
            calificacion = 0; // Evitar división por cero
        } else {
            // Calculamos la proporción de trazos por caracter
            double ratio = (double) totalTrazos / largoFrase;

            // Asignamos calificación basada en rangos (mi criterio subjetivo)
            if (ratio >= 0.9 && ratio <= 1.6) {
                calificacion = 10; // Rango ideal: escritura clara, un trazo por letra aprox.
            } else if (ratio >= 0.7 && ratio < 0.9) {
                calificacion = 9;  // Ligeramente ligada
            } else if (ratio > 1.6 && ratio <= 2.0) {
                calificacion = 8;  // Ligeramente fragmentada
            } else if (ratio >= 0.5 && ratio < 0.7) {
                calificacion = 7;  // Bastante ligada (cursiva)
            } else if (ratio > 2.0 && ratio <= 2.5) {
                calificacion = 6;  // Bastante fragmentada
            } else if (ratio >= 0.3 && ratio < 0.5) {
                calificacion = 5;  // Muy ligada
            } else if (ratio > 2.5 && ratio <= 3.0) {
                calificacion = 4;  // Muy fragmentada o con ruido
            } else if (ratio > 3.0) {
                calificacion = 3;  // Demasiado ruido o trazos muy pequeños
            } else {
                calificacion = 2;  // Muy pocos trazos, escritura incompleta
            }
        }

        if (totalTrazos == 0) {
            calificacion = 0; // No se detectó nada
        }


        String resultadoFinal = "Calificación de Caligrafía: " + calificacion + "/10";
        statusText.setText(resultadoFinal);
        Toast.makeText(this, resultadoFinal, Toast.LENGTH_LONG).show();
    }


    private String normalizarTexto(String texto) {
        if (texto == null) return "";
        return texto.toLowerCase()
                .replaceAll("[^a-z0-9]", "");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            abrirCamara();
        } else {
            Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show();
        }
    }
}
