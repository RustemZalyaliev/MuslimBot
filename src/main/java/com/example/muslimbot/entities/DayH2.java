package com.example.muslimbot.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "Prayer_schedule")
public class DayH2 {

    @Id
    private String date;
    private int muslimDate;
    private int muslimMonth;
    private int hijraYear;
    private String prayerTimes;

    public DayH2() {
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public int getMuslimDate() {
        return muslimDate;
    }

    public void setMuslimDate(int muslimDate) {
        this.muslimDate = muslimDate;
    }

    public int getMuslimMonth() {
        return muslimMonth;
    }

    public void setMuslimMonth(int muslimMonth) {
        this.muslimMonth = muslimMonth;
    }

    public int getHijraYear() {
        return hijraYear;
    }

    public void setHijraYear(int hijraYear) {
        this.hijraYear = hijraYear;
    }

    public String getPrayerTimes() {
        return prayerTimes;
    }

    public void setPrayerTimes(String prayerTimes) {
        this.prayerTimes = prayerTimes;
    }

    @Override
    public String toString() {
        return "DayH2{" +
                "date='" + date + '\'' +
                ", muslimDate=" + muslimDate +
                ", muslimMonth=" + muslimMonth +
                ", hijraYear=" + hijraYear +
                ", prayerTimes='" + prayerTimes + '\'' +
                '}';
    }

}
