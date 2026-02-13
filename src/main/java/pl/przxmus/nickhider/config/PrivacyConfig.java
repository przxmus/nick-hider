package pl.przxmus.nickhider.config;

public final class PrivacyConfig {
    public boolean hideLocalName;
    public boolean hideLocalSkin;
    public boolean hideOtherNames;
    public boolean hideOtherSkins;
    public String localName;
    public String localSkinUser;
    public String othersNameTemplate;
    public String othersSkinUser;

    public PrivacyConfig() {
        this.hideLocalName = true;
        this.hideLocalSkin = true;
        this.hideOtherNames = false;
        this.hideOtherSkins = false;
        this.localName = "Player";
        this.localSkinUser = "";
        this.othersNameTemplate = "Player_[ID]";
        this.othersSkinUser = "";
    }

    public PrivacyConfig copy() {
        PrivacyConfig copy = new PrivacyConfig();
        copy.hideLocalName = this.hideLocalName;
        copy.hideLocalSkin = this.hideLocalSkin;
        copy.hideOtherNames = this.hideOtherNames;
        copy.hideOtherSkins = this.hideOtherSkins;
        copy.localName = this.localName;
        copy.localSkinUser = this.localSkinUser;
        copy.othersNameTemplate = this.othersNameTemplate;
        copy.othersSkinUser = this.othersSkinUser;
        return copy;
    }
}
