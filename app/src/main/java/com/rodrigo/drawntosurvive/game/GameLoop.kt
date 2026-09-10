package com.rodrigo.drawntosurvive.game

class GameLoop(private val update: (Float) -> Unit, private val render: () -> Unit) :
    Thread("DrawnToSurvive-Loop") {
    @Volatile
    private var active = true
    override fun run() {
        GameLog.debug("GameLoop iniciado");
        var last = System.nanoTime(); while (active) {
            val now = System.nanoTime();
            val dt = (now - last) / 1_000_000_000f; last = now; update(dt); render();
            val spent = System.nanoTime() - now;
            val sleep = 16_666_667L - spent; if (sleep > 0) try {
                sleep(sleep / 1_000_000, (sleep % 1_000_000).toInt())
            } catch (_: InterruptedException) {
            }
        }; GameLog.debug("GameLoop encerrado")
    }

    fun shutdown() {
        GameLog.debug("GameLoop.stop chamado"); active = false; interrupt()
    }
}
