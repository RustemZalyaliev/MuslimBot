package com.example.muslimbot.pojo;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

@Component
public class Day {

    private LocalDate date;
    private int muslimDate;
    private String muslimMonth;
    private int hijraYear;
    private Map<String, LocalTime> prayerTimes;

    public Day() {
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public int getMuslimDate() {
        return muslimDate;
    }

    public void setMuslimDate(int muslimDate) {
        this.muslimDate = muslimDate;
    }

    public String getMuslimMonth() {
        return muslimMonth;
    }

    public void setMuslimMonth(String muslimMonth) {
        this.muslimMonth = muslimMonth;
    }

    public int getHijraYear() {
        return hijraYear;
    }

    public void setHijraYear(int hijraYear) {
        this.hijraYear = hijraYear;
    }

    public Map<String, LocalTime> getPrayerTimes() {
        return prayerTimes;
    }

    public void setPrayerTimes(Map<String, LocalTime> prayerTimes) {
        this.prayerTimes = prayerTimes;
    }

    @Override
    public String toString() {
        return "Day{" +
                "date=" + date +
                ", muslimDate=" + muslimDate +
                ", muslimMonth='" + muslimMonth + '\'' +
                ", hijraYear=" + hijraYear +
                ", prayerTimes=" + prayerTimes +
                '}';
    }

}
