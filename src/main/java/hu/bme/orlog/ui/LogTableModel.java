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
        this.rows = List.copyOf(log); // create an immutable snapshot of the current log
        fireTableDataChanged();       // notify the JTable that the whole data set changed
    }

    /**
     * Returns the number of rows (log lines) currently stored.
     *
     * @return row count
     */
    @Override
    public int getRowCount() {
        return rows.size();
    }

    /**
     * Returns the number of columns in the table model (always 1).
     *
     * @return column count
     */
    @Override
    public int getColumnCount() {
        return 1;
    }

    /**
     * Returns the column display name.
     *
     * @param c column index
     * @return column name
     */
    @Override
    public String getColumnName(int c) {
        return "Log";
    }

    /**
     * Returns the cell value at the given row and column. The model has a
     * single column containing the log text.
     *
     * @param r row index
     * @param c column index
     * @return cell value (String)
     */
    @Override
    public Object getValueAt(int r, int c) {
        return rows.get(r);
    }
}
