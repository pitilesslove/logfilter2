package com.logfilter;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DxLogPaser implements ILogParser {

    private static final Set<String> FATAL_SET =
            Collections.unmodifiableSet(Stream.of("FATAL", "F").collect(Collectors.toSet()));
    private static final Set<String> ERROR_SET =
            Collections.unmodifiableSet(Stream.of("ERROR", "E", "3").collect(Collectors.toSet()));
    private static final Set<String> WARN_SET =
            Collections.unmodifiableSet(Stream.of("WARN", "W", "4").collect(Collectors.toSet()));
    private static final Set<String> INFO_SET =
            Collections.unmodifiableSet(Stream.of("INFO", "I", "5").collect(Collectors.toSet()));
    private static final Set<String> DEBUG_SET =
            Collections.unmodifiableSet(Stream.of("DEBUG", "D", "6").collect(Collectors.toSet()));


    @Override
    public LogInfo parseLog(String strText) {

        String logFormatRegex = "^\\[.*\\]\\[.*\\]\\[.*\\]\\[.*\\]\\|?.*";
        Pattern pattern = Pattern.compile(logFormatRegex);
        LogInfo log = new LogInfo();

        if (pattern.matcher(strText).matches()) {

            // 결과를 저장할 변수
            String[] parts = strText.split("\\[|\\]\\[|\\]\\|", -1);

            log.m_strDateTime = parts[1];
            log.m_strLogLV = parts[2];
            log.m_strClz = parts[3];
            log.m_strMdc = parts[4];
            log.m_strMessage = strText.split("\\|", 2)[1];
            log.m_TextColor = getColor(log);

            return log;
        } else {
            log.m_strMessage = strText;
            return log;
        }
    }

    @Override
    public Color getColor(LogInfo logInfo) {
        if(logInfo.m_strLogLV == null) return Color.BLACK;

        if (FATAL_SET.contains(logInfo.m_strLogLV.trim()))
            return new Color(LogColor.COLOR_FATAL);
        if (ERROR_SET.contains(logInfo.m_strLogLV.trim()))
            return new Color(LogColor.COLOR_ERROR);
        else if (WARN_SET.contains(logInfo.m_strLogLV.trim()))
            return new Color(LogColor.COLOR_WARN);
        else if (INFO_SET.contains(logInfo.m_strLogLV.trim()))
            return new Color(LogColor.COLOR_INFO);
        else if (DEBUG_SET.contains(logInfo.m_strLogLV.trim()))
            return new Color(LogColor.COLOR_DEBUG);
        else if (logInfo.m_strLogLV.equals("0"))
            return new Color(LogColor.COLOR_0);
        else if (logInfo.m_strLogLV.equals("1"))
            return new Color(LogColor.COLOR_1);
        else if (logInfo.m_strLogLV.equals("2"))
            return new Color(LogColor.COLOR_2);
        else if (logInfo.m_strLogLV.equals("5"))
            return new Color(LogColor.COLOR_5);
        else
            return Color.BLACK;
    }

    @Override
    public int getLogLV(LogInfo logInfo) {
        if(logInfo.m_strLogLV == null) return LogInfo.LOG_LV_VERBOSE;

        if (FATAL_SET.contains(logInfo.m_strLogLV.trim()))
            return LogInfo.LOG_LV_FATAL;
        if (ERROR_SET.contains(logInfo.m_strLogLV.trim()))
            return LogInfo.LOG_LV_ERROR;
        else if (WARN_SET.contains(logInfo.m_strLogLV.trim()))
            return LogInfo.LOG_LV_WARN;
        else if(INFO_SET.contains(logInfo.m_strLogLV.trim()))
            return LogInfo.LOG_LV_INFO;
        else if (DEBUG_SET.contains(logInfo.m_strLogLV.trim()))
            return LogInfo.LOG_LV_DEBUG;
        else
            return LogInfo.LOG_LV_VERBOSE;
    }
}
