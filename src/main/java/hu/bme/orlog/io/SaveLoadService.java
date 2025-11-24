package hu.bme.orlog.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import hu.bme.orlog.model.GameState;

/**
 * Service responsible for saving and loading the GameState to/from a file.
 *
 * This class provides simple serialization-based persistence using Java's
 * ObjectOutputStream and ObjectInputStream. It does not perform
 * any format versioning or compatibility handling beyond Java serialization.
 */
public class SaveLoadService {

    /**
     * Saves the provided GameState into the given file using Java serialization.
     *
     * @param gs the game state to save; must be serializable
     * @param f  the target file to write into; parent directories must exist and be writable
     * @throws IOException if an I/O error occurs during writing
     */
    public void save(GameState gs, File f) throws IOException {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(f))) {
            out.writeObject(gs);
        }
    }

    /**
     * Loads a GameState instance from the provided file using Java serialization.
     *
     * @param f the source file to read from; must exist and be readable
     * @return the loaded GameState instance
     * @throws IOException            if an I/O error occurs during reading
     * @throws ClassNotFoundException if the serialized class cannot be found during deserialization
     */
    public GameState load(File f) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
            return (GameState) in.readObject();
        }
    }
}
