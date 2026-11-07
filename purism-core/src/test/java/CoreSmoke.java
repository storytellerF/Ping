import com.live2d.sdk.cubism.core.*;
import java.nio.file.*;

/** Device-side JNI lifecycle and Java array synchronization check. */
public final class CoreSmoke {
    public static void main(String[] args) throws Exception {
        for (String path : args) {
            byte[] bytes = Files.readAllBytes(Paths.get(path));
            for (int cycle = 0; cycle < 3; cycle++) {
                try (CubismMoc moc = CubismMoc.instantiate(bytes);
                     CubismModel model = moc.instantiateModel()) {
                    CubismParameters parameters = model.getParameters();
                    if (parameters.getCount() == 0 || model.getDrawables().getCount() == 0)
                        throw new AssertionError("Empty model");
                    float[][] initial = model.getDrawables().getVertexPositions();
                    float[] original = initial[0].clone();
                    boolean changed = false;
                    for (int frame = 0; frame < 120; frame++) {
                        for (int i = 0; i < parameters.getCount(); i++) {
                            parameters.getValues()[i] = parameters.getMinimumValues()[i]
                                + (parameters.getMaximumValues()[i] - parameters.getMinimumValues()[i])
                                * (float)(0.5 + 0.5 * Math.sin(frame * 0.1));
                            if (parameters.getTypes()[i] == null) throw new AssertionError("Missing type");
                        }
                        model.update();
                        for (float[] row : model.getDrawables().getVertexPositions())
                            for (float value : row) if (!Float.isFinite(value)) throw new AssertionError("Invalid vertex");
                        float[] current = model.getDrawables().getVertexPositions()[0];
                        for (int i = 0; i < original.length; i++) changed |= current[i] != original[i];
                        model.resetDrawableDynamicFlags();
                    }
                    if (!changed) throw new AssertionError("Java output did not update");
                }
            }
            System.out.println("PASS " + Paths.get(path).getFileName() + " 3 lifecycles / 360 updates");
        }
    }
}
