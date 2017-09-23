package com.ultraflynn.unistats;

import java.time.LocalDate;

public class Action {
    public final LocalDate date;
    public final String applicant;
    public final String officer;
    public final String action;

    public Action(LocalDate date, String applicant, String officer, String action) {
        this.date = date;
        this.applicant = applicant;
        this.officer = officer;
        this.action = action;
    }

    @Override
    public String toString() {
        return "Action{" +
                "date=" + date +
                ", applicant='" + applicant + '\'' +
                ", officer='" + officer + '\'' +
                ", action='" + action + '\'' +
                '}';
    }
}
