package com.lucasrocha.craftai.client.presentation;

import java.util.List;

public final class CraftAiChatPresenterTest {

    private CraftAiChatPresenterTest() {}

    public static void main(String[] args) {
        keepsShortMessagesTogether();
        prefersReadableSplitBoundaries();
        hardSplitsUnbrokenText();
        System.out.println("CraftAiChatPresenterTest: 3 groups passed");
    }

    private static void keepsShortMessagesTogether() {
        assertEquals(List.of("A short answer."),
                CraftAiChatPresenter.splitMessage(" A short answer. ", 100));
    }

    private static void prefersReadableSplitBoundaries() {
        String message = "First sentence is easy to read. Second sentence also stays readable.";
        List<String> chunks = CraftAiChatPresenter.splitMessage(message, 42);
        assertEquals(2, chunks.size());
        assertEquals("First sentence is easy to read.", chunks.getFirst());
        assertEquals("Second sentence also stays readable.", chunks.getLast());
    }

    private static void hardSplitsUnbrokenText() {
        List<String> chunks = CraftAiChatPresenter.splitMessage("abcdefghij", 4);
        assertEquals(List.of("abcd", "efgh", "ij"), chunks);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
