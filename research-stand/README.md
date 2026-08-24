# research-stand

Стенды, которыми получены цифры и факты из [../docs/research/architecture.md](../docs/research/architecture.md).
Не часть продукта: они существуют, чтобы утверждение можно было перепроверить, а не поверить на слово.

## resolve

Потребительская сборка: объявляет одну зависимость и печатает свой `compileClasspath` — то, что
получит чужой человек, а не то, что объявил автор.

```bash
cd resolve && ./gradlew -q reportCompileClasspath -Pcoordinate=io.github.youndie:kompot-core:0.27.0.46
```

Печатает `CP kompot-core-jvm-0.27.0.jar` на запрос версии `0.27.0.46` — расхождение из §1.3 ресёрча.
