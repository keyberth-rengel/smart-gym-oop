package com.smartgym;

import com.smartgym.console.ConsoleApp;
import com.smartgym.service.SmartGymService;

public class SmartGymApp {
    public static void main(String[] args) {
        SmartGymService service = new SmartGymService();
        new ConsoleApp(service).run();
    }
}