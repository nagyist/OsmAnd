package net.osmand.search.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class HashSkipTileQuadTreeTest {

    static class TestObject {
        final long id;
        final String name;

        TestObject(long id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name + " (#" + id + ")";
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Запуск тестов HashSkipTileQuadTree ===");

        HashSkipTileQuadTree<TestObject> tree = new HashSkipTileQuadTree<>();
        Random rnd = new Random(42); // Фиксированный seed для воспроизводимости

        // 1. Генерируем 50 000 объектов по всей карте в 31-битной системе координат
        int numObjects = 50_000;
        int maxCoord = Integer.MAX_VALUE;

        List<TestObject> allObjects = new ArrayList<>();
        List<int[]> allBBoxes = new ArrayList<>();

        for (int i = 0; i < numObjects; i++) {
            int minX = Math.abs(rnd.nextInt(maxCoord - 1000000));
            int minY = Math.abs(rnd.nextInt(maxCoord - 1000000));
            int maxX = minX + rnd.nextInt(200000);
            int maxY = minY + rnd.nextInt(200000);

            int[] bbox = new int[] { minX, minY, maxX, maxY };
            TestObject obj = new TestObject(i, "POI_" + i);

            allObjects.add(obj);
            allBBoxes.add(bbox);

            tree.addObject(obj, bbox, i);
        }

        tree.build();
        System.out.println("Дерево успешно построено для " + numObjects + " объектов.\n");

        // 2. Тестовый сценарий: Выполняем серии случайных пространственных запросов
        int testQueries = 50;
        long globalTotalEntries = 0;
        long globalInspectedEntries = 0;
        long globalSkippedItems = 0;

        for (int q = 0; q < testQueries; q++) {
            // Генерируем Query BBox порядка ~1-2% от размера карты
            int qMinX = Math.abs(rnd.nextInt(maxCoord - 30000000));
            int qMinY = Math.abs(rnd.nextInt(maxCoord - 30000000));
            int qMaxX = qMinX + 15000000;
            int qMaxY = qMinY + 15000000;

            int[] queryBBox = new int[] { qMinX, qMinY, qMaxX, qMaxY };

            // А) Эталонный результат (Brute Force по всему исходному списку)
            Set<Long> expectedObjIds = new HashSet<>();
            for (int i = 0; i < numObjects; i++) {
                if (intersectsBBox(allBBoxes.get(i), queryBBox)) {
                    expectedObjIds.add(allObjects.get(i).id);
                }
            }

            // Б) Результат через TileIterator со сбором метрик SkipStats
            Set<Long> actualObjIds = new HashSet<>();

            for (int z = HashSkipTileQuadTree.MIN_ZOOM; z <= HashSkipTileQuadTree.MAX_ZOOM; z++) {
                HashSkipTileQuadTree.ZoomBucket bucket = tree.getZoomBucket(z);
                if (bucket == null || bucket.len == 0) continue;

                int levelsCount = bucket.indexedZooms.length;
                HashSkipTileQuadTree.SkipStats stats = new HashSkipTileQuadTree.SkipStats(levelsCount);

                var it = tree.new TileIterator(bucket, queryBBox, stats);
                while (it.hasNext()) {
                    var entry = it.next();
                    actualObjIds.add(entry.obj.id);
                }

                globalTotalEntries += bucket.len;
                globalInspectedEntries += stats.inspectedEntries;
                globalSkippedItems += stats.totalElementsSkipped;

                // Для первого запроса выведем подробный отчёт по первому бакету со скипами
                if (q == 0 && stats.totalSkipsCount > 0) {
                    System.out.println("--- Детализация SkipStats для Zoom Bucket Z" + z + " ---");
                    stats.printStats(bucket.len, bucket.indexedZooms);
                    System.out.println();
                }
            }

            // В) Валидация точности поиска
            if (!expectedObjIds.equals(actualObjIds)) {
                System.err.println("❌ ОШИБКА НА ЗАПРОСЕ #" + q);
                System.err.println("Ожидалось объектов: " + expectedObjIds.size() + ", найдено: " + actualObjIds.size());
                
                Set<Long> missing = new HashSet<>(expectedObjIds);
                missing.removeAll(actualObjIds);
                if (!missing.isEmpty()) System.err.println("Потерянные ID: " + missing);

                throw new AssertionError("Тест провален: результат поиска отличается от эталона!");
            }
        }

        // 3. Валидация эффективности алгоритма пропуска (Skip Logic)
        double inspectRatio = (double) globalInspectedEntries / globalTotalEntries * 100.0;
        System.out.println("=== ИТОГО ПО ВСЕМ " + testQueries + " ЗАПРОСАМ ===");
        System.out.printf("Всего элементов в обработанных бакетах : %d\n", globalTotalEntries);
        System.out.printf("Реально проинспектировано итератором  : %d (%.2f%%)\n", globalInspectedEntries, inspectRatio);
        System.out.printf("Всего пропущено элементов по скипам   : %d\n", globalSkippedItems);

        // Жёсткие Assertions
        if (globalSkippedItems == 0) {
            throw new AssertionError("❌ СКИПЫ НЕ РАБОТАЮТ! Ни один элемент не был пропущен.");
        }

        if (inspectRatio > 30.0) {
            throw new AssertionError(String.format("❌ Низкая эффективность: просмотрено %.2f%% элементов!", inspectRatio));
        }

        System.out.println("\n✅ ВСЕ ТЕСТЫ УСПЕШНО ПРОЙДЕНЫ!");
    }

    private static boolean intersectsBBox(int[] a, int[] b) {
        return a[0] <= b[2] && a[2] >= b[0] && a[1] <= b[3] && a[3] >= b[1];
    }
}