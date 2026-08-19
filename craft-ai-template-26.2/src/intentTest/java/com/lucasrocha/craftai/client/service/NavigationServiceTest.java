package com.lucasrocha.craftai.client.service;

import com.lucasrocha.craftai.client.data.WorldQueryResult;

import java.util.List;

public final class NavigationServiceTest {

    private record TestCase(
            long originX,
            long originZ,
            long destinationX,
            long destinationZ,
            int expectedDistance,
            int expectedDeltaX,
            int expectedDeltaZ,
            WorldQueryResult.Direction expectedDirection
    ) {}

    private static final List<TestCase> CASES = List.of(
            direction(0, -10, WorldQueryResult.Direction.NORTH),
            direction(10, -10, WorldQueryResult.Direction.NORTHEAST),
            direction(10, 0, WorldQueryResult.Direction.EAST),
            direction(10, 10, WorldQueryResult.Direction.SOUTHEAST),
            direction(0, 10, WorldQueryResult.Direction.SOUTH),
            direction(-10, 10, WorldQueryResult.Direction.SOUTHWEST),
            direction(-10, 0, WorldQueryResult.Direction.WEST),
            direction(-10, -10, WorldQueryResult.Direction.NORTHWEST),
            direction(0, 0, WorldQueryResult.Direction.HERE),
            direction(10, 4, WorldQueryResult.Direction.EAST),
            direction(10, 5, WorldQueryResult.Direction.SOUTHEAST),
            direction(10, -4, WorldQueryResult.Direction.EAST),
            direction(10, -5, WorldQueryResult.Direction.NORTHEAST),
            new TestCase(100, -40, 103, -44, 5, 3, -4, WorldQueryResult.Direction.NORTHEAST),
            new TestCase(
                    -17,
                    -34,
                    -272,
                    -1488,
                    1476,
                    -255,
                    -1454,
                    WorldQueryResult.Direction.NORTH
            )
    );

    private NavigationServiceTest() {}

    public static void main(String[] args) {
        for (TestCase testCase : CASES) {
            WorldQueryResult.Navigation actual = NavigationService.calculate(
                    testCase.originX(),
                    testCase.originZ(),
                    testCase.destinationX(),
                    testCase.destinationZ()
            );
            assertEquals("distance", testCase.expectedDistance(), actual.distanceBlocks());
            assertEquals("delta X", testCase.expectedDeltaX(), actual.deltaXBlocks());
            assertEquals("delta Z", testCase.expectedDeltaZ(), actual.deltaZBlocks());
            assertEquals("direction", testCase.expectedDirection(), actual.direction());
        }

        System.out.println("NavigationServiceTest: " + CASES.size() + " cases passed");
    }

    private static TestCase direction(
            int deltaX,
            int deltaZ,
            WorldQueryResult.Direction direction
    ) {
        int distance = (int) Math.round(Math.hypot(deltaX, deltaZ));
        return new TestCase(0, 0, deltaX, deltaZ, distance, deltaX, deltaZ, direction);
    }

    private static void assertEquals(String field, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    "Expected " + field + " " + expected + " but got " + actual
            );
        }
    }
}
