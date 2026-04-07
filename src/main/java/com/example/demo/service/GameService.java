package com.example.demo.service;

import org.springframework.stereotype.Service;

import com.example.demo.model.GameRule;

@Service
public class GameService {
    public GameRule getHowToPlay() {
        return new GameRule();
    }
}
