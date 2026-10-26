package net.wiknzylix.playtimetogether.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerRuleMatcherTest {
    @Test
    void keywordDelimitersTrimAndIgnoreCase() {
        var matcher = new PlayerRuleMatcher(" Alice, BOB； 工厂，spawn; Keeper\r\nMiNeR\n ", "", false, false);
        for (String id : new String[] {"MyALICE", "bob_2", "北方工厂01", "SPAWN_backup", "keeper", "TheMiner"}) {
            assertTrue(matcher.matches(id, false), id);
        }
        assertFalse(matcher.matches("Carol", false));
        assertNull(matcher.regexError());
    }

    @Test
    void blankKeywordsDoNotMatchEveryPlayer() {
        var matcher = new PlayerRuleMatcher(" ,，;；\r\n\t ", "   ", false, false);
        assertFalse(matcher.matches("Alice", false));
        assertFalse(matcher.matches("", false));
        assertFalse(matcher.matches(null, false));
        assertNull(matcher.regexError());
    }

    @Test
    void keywordsAreLiteralSubstringsRatherThanRegularExpressions() {
        var matcher = new PlayerRuleMatcher("bot.*,name[1]", null, false, false);
        assertTrue(matcher.matches("TheBOT.*Backup", false));
        assertTrue(matcher.matches("name[1]_old", false));
        assertFalse(matcher.matches("bot123", false));
    }

    @Test
    void regularExpressionFindsCaseInsensitiveUnicodeMatches() {
        var matcher = new PlayerRuleMatcher("", "bot_[0-9]+|äpfel", false, false);
        assertTrue(matcher.matches("prefix_BOT_123_backup", false));
        assertTrue(matcher.matches("ÄPFEL", false));
        assertFalse(matcher.matches("bot_words", false));
        assertNull(matcher.regexError());
    }

    @Test
    void explicitRegexAnchorsStillRequireTheWholeName() {
        var matcher = new PlayerRuleMatcher(null, "^bot_[0-9]+$", false, false);
        assertTrue(matcher.matches("BOT_123", false));
        assertFalse(matcher.matches("prefix_bot_123", false));
        assertFalse(matcher.matches("bot_123_suffix", false));
    }

    @Test
    void numericRuleRequiresNonemptyAsciiDigitsOnly() {
        var matcher = new PlayerRuleMatcher(null, null, true, false);
        for (String id : new String[] {"0", "0123456789", "123456789012345678901234567890"}) {
            assertTrue(matcher.matches(id, false), id);
        }
        for (String id : new String[] {"", "１２３", "١٢٣", "+123", "-123", "123a", " 123", "123 ", "1.2", "123\n"}) {
            assertFalse(matcher.matches(id, false), id);
        }
    }

    @Test
    void fakeRuleUsesOnlyTheExplicitServerFlag() {
        var matcher = new PlayerRuleMatcher("", "", false, true);
        assertTrue(matcher.matches("Alice", true));
        assertTrue(matcher.matches("123", true));
        assertFalse(matcher.matches("bot_fake_123", false));
        assertFalse(matcher.matches("carpet.patches.EntityPlayerMPFake", false));
        assertFalse(new PlayerRuleMatcher("", "", false, false).matches("Alice", true));
    }

    @Test
    void allEnabledRulesAreCombinedWithOr() {
        var matcher = new PlayerRuleMatcher("factory", "^keeper_", true, true);
        assertTrue(matcher.matches("west_FACTORY", false));
        assertTrue(matcher.matches("Keeper_A", false));
        assertTrue(matcher.matches("00012", false));
        assertTrue(matcher.matches("ordinary", true));
        assertFalse(matcher.matches("ordinary", false));
    }

    @Test
    void invalidRegexIsReportedAndOnlyThatRuleIsIgnored() {
        var matcher = new PlayerRuleMatcher("factory", "[", true, true);
        assertNotNull(matcher.regexError());
        assertFalse(matcher.regexError().isBlank());
        assertTrue(matcher.matches("factory_a", false));
        assertTrue(matcher.matches("123", false));
        assertTrue(matcher.matches("Carpet player", true));
        assertFalse(matcher.matches("Alice", false));
        assertFalse(new PlayerRuleMatcher("", "[", false, false).matches("[", false));
    }

    @Test
    void repeatedCallsDoNotShareRegexMatcherState() {
        var matcher = new PlayerRuleMatcher(" Alice ", "bot[0-9]", false, false);
        for (int i = 0; i < 10; i++) {
            assertTrue(matcher.matches("bot1", false));
            assertFalse(matcher.matches("Carol", false));
            assertTrue(matcher.matches("ALICE", false));
        }
    }
}
