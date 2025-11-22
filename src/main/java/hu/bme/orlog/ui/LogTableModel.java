package hu.bme.orlog.ui;

import java.util.Deque;
import java.util.List;

import javax.swing.table.AbstractTableModel;

public class LogTableModel extends AbstractTableModel {
    private List<String> rows = List.of();

    public void setLog(Deque<String> log) {
        this.rows = List.copyOf(log);
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return 1;
    }

    @Override
    public String getColumnName(int c) {
        return "Log";
    }

    @Override
    public Object getValueAt(int r, int c) {
        return rows.get(r);
    }
}
