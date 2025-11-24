package hu.bme.orlog.ui;

import java.util.Deque;
import java.util.List;

import javax.swing.table.AbstractTableModel;

/**
 * Simple table model that exposes the game log for display in a JTable.
 *
 * The model stores an immutable copy of the provided Deque of log lines
 * and notifies listeners when the log is updated.
 */
public class LogTableModel extends AbstractTableModel {
    private List<String> rows = List.of();

    /**
     * Replace the table contents with the given log lines.
     *
     * @param log deque of log lines (most recent at the end)
     */
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
