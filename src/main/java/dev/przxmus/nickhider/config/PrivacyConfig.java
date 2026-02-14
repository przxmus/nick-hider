package dev.przxmus.nickhider.config;

public final class PrivacyConfig {
    public boolean enabled;
    public boolean hideLocalName;
    public boolean hideLocalSkin;
    public boolean hideLocalCape;
    public boolean hideOtherNames;
    public boolean hideOtherSkins;
    public boolean hideOtherCapes;
    public String localName;
    public String localSkinUser;
    public String localCapeUser;
    public String othersNameTemplate;
    public String othersSkinUser;
    public String othersCapeUser;

    public PrivacyConfig() {
        this.enabled = true;
        this.hideLocalName = true;
        this.hideLocalSkin = true;
        this.hideLocalCape = true;
        this.hideOtherNames = false;
        this.hideOtherSkins = false;
        this.hideOtherCapes = false;
        this.localName = "Player";
        this.localSkinUser = "";
        this.localCapeUser = "";
        this.othersNameTemplate = "Player_[ID]";
        this.othersSkinUser = "";
        this.othersCapeUser = "";
    }

    public PrivacyConfig copy() {
        PrivacyConfig copy = new PrivacyConfig();
        copy.enabled = this.enabled;
        copy.hideLocalName = this.hideLocalName;
        copy.hideLocalSkin = this.hideLocalSkin;
        copy.hideLocalCape = this.hideLocalCape;
        copy.hideOtherNames = this.hideOtherNames;
        copy.hideOtherSkins = this.hideOtherSkins;
        copy.hideOtherCapes = this.hideOtherCapes;
        copy.localName = this.localName;
        copy.localSkinUser = this.localSkinUser;
        copy.localCapeUser = this.localCapeUser;
        copy.othersNameTemplate = this.othersNameTemplate;
        copy.othersSkinUser = this.othersSkinUser;
        copy.othersCapeUser = this.othersCapeUser;
        return copy;
    }
}
