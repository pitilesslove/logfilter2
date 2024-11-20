package com.logfilter;

import java.io.ObjectInputStream.GetField;
import java.util.ArrayList;

import javax.swing.table.AbstractTableModel;

public class LogFilterTableModel extends AbstractTableModel {
    static final int        COMUMN_LINE         = 0;
    static final int        COMUMN_DATE_TIME    = 1;
    static final int        COMUMN_LOGLV        = 2;
    static final int        COMUMN_CLZ          = 3;
    static final int        COMUMN_MDC          = 4;
    static final int        COMUMN_BOOKMARK     = 5;
    static final int        COMUMN_MESSAGE      = 6;
    public static final int COMUMN_MAX          = 7;

    private static final long serialVersionUID = 1L;

    public static String  ColName[]     = { "Line", "DateTime", "Lv", "className", "MDC", "Bookmark", "Message" };
    public static int     ColWidth[]    = {  50,     170,        30,      110,      200,   100,        1150};
    public static int     DEFULT_WIDTH[]= {  50,     170,        30,      110,      200,   100,        1150};
    
    ArrayList<LogInfo> m_arData;

    public static void setColumnWidth(int nColumn, int nWidth) {
        T.d("nWidth = " + nWidth);
        if(nWidth >= DEFULT_WIDTH[nColumn])
            ColWidth[nColumn] = nWidth;
    }

    public int getColumnCount()
    {
        return ColName.length;
    }

    public int getRowCount() {
        if (m_arData != null)
            return m_arData.size();
        else
            return 0;
    }

    public String getColumnName(int col) {
        return ColName[col];
    }
    
    public Object getValueAt(int rowIndex, int columnIndex)
    {
        return m_arData.get(rowIndex).getData(columnIndex);
    }
    
    public LogInfo getRow(int row) {
        return m_arData.get(row);
    }
    
    public void setData(ArrayList<LogInfo> arData)
    {
        m_arData = arData;
    }
}
