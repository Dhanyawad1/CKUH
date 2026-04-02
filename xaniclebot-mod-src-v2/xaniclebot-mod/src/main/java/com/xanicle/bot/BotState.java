package com.xanicle.bot;

public enum BotState {
    IDLE,       // Bot is off, player has full control
    FIGHTING,   // Actively crystal PvPing the target
    PEARLING,   // Throwing a pearl to close distance
    RETOTOMING, // Paused to swap totem into offhand
    EATING_GAP  // Paused to eat a golden apple
}
