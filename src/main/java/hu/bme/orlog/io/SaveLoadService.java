package hu.bme.orlog.io;

import hu.bme.orlog.model.GameState;
import java.io.*;

public class SaveLoadService {
    public void save(GameState gs, File f) throws IOException {
        try(ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(f))){
            out.writeObject(gs);
        }
    }
    public GameState load(File f) throws IOException, ClassNotFoundException {
        try(ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))){
            return (GameState) in.readObject();
        }
    }
}
