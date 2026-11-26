package dev.freedomadminpanel.net;

public record PlayerRecord(
    String name,
    int windowDays,
    int warn,
    int kick,
    int mute,
    int jail,
    int ban,
    boolean jailTracked,
    float otherWeight,
    PlayerRecord.Formula muteFormula,
    PlayerRecord.Formula jailFormula,
    PlayerRecord.Formula banFormula,
    int activeWarns,
    int autoBanAt,
    String autoBanDuration
) {
    public boolean autoBanKnown() {
        return this.autoBanAt > 0 && this.activeWarns >= 0;
    }

    public boolean nextWarnBans() {
        return this.autoBanKnown() && this.activeWarns + 1 == this.autoBanAt;
    }

    public int total() {
        return this.warn + this.kick + this.mute + this.jail + this.ban;
    }

    public record Formula(String base, String step, String max) {
    }
}
