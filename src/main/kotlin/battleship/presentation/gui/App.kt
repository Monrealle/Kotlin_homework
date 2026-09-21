package battleship.presentation.gui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import battleship.application.RandomShipPlacer
import battleship.domain.model.*

/**
 * =============================================================================================
 * Главное Compose-приложение администратора «Морского боя».
 *
 * Интерфейс разделён на три вкладки:
 *
 * 1. «Игроки» - создание игроков и просмотр их статистики.
 * 2. «Новая партия» - выбор двух игроков и запуск новой партии.
 * 3. «Текущая партия» - управление активной партией и просмотр истории ходов.
 *
 * @param ctrl контроллер GUI, через который выполняются операции приложения
 * =============================================================================================
 */
@Composable
fun AdminApp(ctrl: GuiController) {

    /* Индекс текущей вкладки. */
    var tab by remember { mutableStateOf(0) }

    /* Список игроков, отображаемый в интерфейсе. */
    val players = remember {
        mutableStateListOf<Player>()
    }

    /**
     * Загружает существующих игроков при первом отображении приложения.
     */
    LaunchedEffect(Unit) {
        players.addAll(ctrl.playerRepo.findAll())
    }

    MaterialTheme {

        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            /**
             * Навигация между разделами приложения.
             */
            TabRow(
                selectedTabIndex = tab
            ) {

                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 }
                ) {
                    Text("Игроки")
                }

                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 }
                ) {
                    Text("Новая партия")
                }

                Tab(
                    selected = tab == 2,
                    onClick = { tab = 2 }
                ) {
                    Text("Текущая партия")
                }
            }

            /**
             * Отображение содержимого выбранной вкладки.
             */
            when (tab) {

                0 -> PlayersTab(
                    ctrl = ctrl,
                    players = players
                )

                1 -> NewGameTab(
                    ctrl = ctrl,
                    players = players,
                    onStart = {
                        tab = 2
                    }
                )

                2 -> GameTab(
                    ctrl = ctrl,
                    onBack = {
                        tab = 0
                    }
                )
            }
        }
    }
}

/**
 * =============================================================================================
 * Вкладка списка игроков.
 *
 * Позволяет:
 *
 * 1. Добавлять новых игроков.
 * 2. Просматривать список существующих игроков.
 * 3. Просматривать текущий рейтинг Эло.
 * 4. Просматривать количество сыгранных партий.
 * 5. Просматривать количество побед и винрейт.
 *
 * @param ctrl контроллер GUI
 * @param players список игроков
 * =============================================================================================
 */
@Composable
fun PlayersTab(
    ctrl: GuiController,
    players: MutableList<Player>
) {

    var name by remember {
        mutableStateOf("")
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    Column(
        modifier = Modifier.padding(16.dp)
    ) {

        /**
         * ---------------------------------------------------------------------------------------------
         * Форма добавления нового игрока.
         * ---------------------------------------------------------------------------------------------
         */
        Text(
            "Добавить игрока",
            fontWeight = FontWeight.Bold
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = {
                    Text("Имя")
                },
                modifier = Modifier.weight(1f)
            )

            Spacer(
                modifier = Modifier.width(8.dp)
            )

            Button(
                onClick = {

                    error = ctrl.addPlayer(name)

                    if (error == null) {

                        /* Обновляем локальный список игроков. */
                        players.clear()
                        players.addAll(
                            ctrl.playerRepo.findAll()
                        )

                        name = ""
                    }
                }
            ) {
                Text("+")
            }
        }

        /**
         * Отображение сообщения об ошибке,
         * если добавить игрока не удалось.
         */
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            "Игроки:",
            fontWeight = FontWeight.Bold
        )

        /**
         * ---------------------------------------------------------------------------------------------
         * Отображение списка игроков и их текущей статистики.
         *
         * Показываются:
         *
         * - имя;
         * - текущий рейтинг Эло;
         * - количество завершённых партий;
         * - количество побед;
         * - винрейт.
         * ---------------------------------------------------------------------------------------------
         */
        players.forEach { p ->

            val stats = ctrl.getStats(p)

            Text(
                "${p.name}  " +
                        "⭐ ${stats.currentElo} | " +
                        "Игр: ${stats.gamesPlayed} | " +
                        "Побед: ${stats.wins} | " +
                        "Винрейт: ${"%.1f".format(stats.winRate * 100)}%"
            )
        }
    }
}

/**
 * =============================================================================================
 * Вкладка создания новой партии.
 *
 * Позволяет выбрать двух разных игроков и автоматически расставляет
 * их корабли перед началом партии.
 *
 * @param ctrl контроллер GUI
 * @param players список доступных игроков
 * @param onStart вызывается после успешного создания партии
 * =============================================================================================
 */
@Composable
fun NewGameTab(
    ctrl: GuiController,
    players: List<Player>,
    onStart: () -> Unit
) {

    var p1 by remember {
        mutableStateOf<Player?>(null)
    }

    var p2 by remember {
        mutableStateOf<Player?>(null)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    Column(
        modifier = Modifier.padding(16.dp)
    ) {

        /**
         * ---------------------------------------------------------------------------------------------
         * Выбор первого игрока.
         *
         * При смене первого игрока автоматически сбрасывается второй,
         * если ранее был выбран тот же игрок.
         * ---------------------------------------------------------------------------------------------
         */
        Text(
            "Игрок 1:",
            fontWeight = FontWeight.Bold
        )

        players.forEach { p ->

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                RadioButton(
                    selected = p1 == p,
                    onClick = {

                        p1 = p

                        if (p2 == p) {
                            p2 = null
                        }

                        error = null
                    }
                )

                Text(p.name)
            }
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        /**
         * ---------------------------------------------------------------------------------------------
         * Выбор второго игрока.
         *
         * Игрок, уже выбранный первым, не отображается в этом списке.
         * Поэтому одна и та же пара игроков невозможна.
         * ---------------------------------------------------------------------------------------------
         */
        Text(
            "Игрок 2:",
            fontWeight = FontWeight.Bold
        )

        players.forEach { p ->

            if (p != p1) {

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    RadioButton(
                        selected = p2 == p,
                        onClick = {

                            p2 = p
                            error = null
                        }
                    )

                    Text(p.name)
                }
            }
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        /**
         * ---------------------------------------------------------------------------------------------
         * Запуск партии.
         *
         * После успешного создания партии корабли обоих игроков
         * автоматически расставляются с помощью [RandomShipPlacer].
         * ---------------------------------------------------------------------------------------------
         */
        Button(
            onClick = {

                val a = p1
                val b = p2

                if (a == null || b == null) {

                    error = "Выберите обоих игроков"

                    return@Button
                }

                error = ctrl.startGame(
                    a,
                    b
                )

                if (error == null) {

                    /**
                     * Автоматически генерируем и устанавливаем
                     * корректную расстановку кораблей для обоих игроков.
                     */
                    val firstPlacement =
                        ctrl.placeShips(
                            a,
                            RandomShipPlacer.generate()
                        )

                    if (!firstPlacement.isValid) {

                        error = firstPlacement.errors.joinToString("; ")

                        return@Button
                    }

                    val secondPlacement =
                        ctrl.placeShips(
                            b,
                            RandomShipPlacer.generate()
                        )

                    if (!secondPlacement.isValid) {

                        error = secondPlacement.errors.joinToString("; ")

                        return@Button
                    }

                    /* После успешного запуска переходим к текущей партии. */
                    onStart()
                }
            }
        ) {
            Text("Начать партию")
        }

        /**
         * Отображение ошибки создания партии.
         */
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * =============================================================================================
 * Вкладка текущей партии.
 *
 * Отображает состояние активной игры:
 *
 * - ожидание расстановки кораблей;
 * - текущего игрока;
 * - ввод координаты выстрела;
 * - историю ходов;
 * - результат завершённой партии;
 * - изменения рейтинга Эло.
 *
 * Compose не отслеживает изменения обычных Mutable-полей объекта [Game].
 * Поэтому используется локальная переменная `revision`, изменение которой
 * принудительно вызывает перерисовку содержимого партии.
 *
 * @param ctrl контроллер GUI
 * @param onBack переход обратно к списку игроков
 * =============================================================================================
 */
@Composable
fun GameTab(
    ctrl: GuiController,
    onBack: () -> Unit
) {

    /**
     * Счётчик изменений состояния игры.
     *
     * Увеличивается после расстановки кораблей и каждого хода,
     * чтобы Compose повторно отрисовал интерфейс.
     */
    var revision by remember {
        mutableIntStateOf(0)
    }

    val game = ctrl.game

    if (game == null) {

        Text(
            "Нет активной партии",
            modifier = Modifier.padding(16.dp)
        )

        return
    }

    /**
     * key(revision) заставляет Compose пересоздать состояние
     * содержимого при изменении партии.
     */
    key(revision) {

        GameContent(
            ctrl = ctrl,
            game = game,
            onRefresh = {
                revision++
            },
            onBack = onBack
        )
    }
}

/**
 * =============================================================================================
 * Содержимое вкладки текущей партии.
 *
 * Вынесено из [GameTab], чтобы обновление через `key(revision)`
 * не требовало использования ранних `return` внутри Compose-блока.
 *
 * @param ctrl контроллер GUI
 * @param game текущая партия
 * @param onRefresh функция принудительного обновления интерфейса
 * @param onBack возврат к списку игроков
 * =============================================================================================
 */
@Composable
private fun GameContent(
    ctrl: GuiController,
    game: Game,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {

    /**
     * Полноразмерный контейнер текущей партии.
     */
    Column(
        modifier = Modifier.fillMaxSize()
            .padding(16.dp)
    ) {

        /**
         * ---------------------------------------------------------------------------------------------
         * Завершённая партия.
         *
         * Показывается победитель и изменения рейтинга Эло.
         * ---------------------------------------------------------------------------------------------
         */
        if (game.status == GameStatus.FINISHED) {

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    "Победитель: ${game.winner?.name}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                /**
                 * Вывод изменений рейтинга обоих игроков.
                 */
                game.eloChanges?.forEach { (_, change) ->

                    Text(
                        "${change.player.name}: " +
                                "${change.oldRating} → ${change.newRating} " +
                                "(${if (change.delta > 0) "+" else ""}${change.delta})"
                    )
                }

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                Button(
                    onClick = {

                        ctrl.finish()
                        onBack()
                    }
                ) {
                    Text("Вернуться к списку")
                }
            }

            return
        }

        /**
         * ---------------------------------------------------------------------------------------------
         * Фаза расстановки кораблей.
         *
         * В GUI используется автоматическая корректная расстановка.
         * ---------------------------------------------------------------------------------------------
         */
        if (
            game.status == GameStatus.SETUP_P1 ||
            game.status == GameStatus.SETUP_P2
        ) {

            val currentPlayer =
                if (game.status == GameStatus.SETUP_P1) {
                    game.player1
                } else {
                    game.player2
                }

            Text(
                "Ожидание расстановки кораблей для ${currentPlayer.name}",
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Button(
                onClick = {

                    val result =
                        ctrl.placeShips(
                            currentPlayer,
                            RandomShipPlacer.generate()
                        )

                    if (result.isValid) {
                        onRefresh()
                    }
                }
            ) {
                Text("Авторасставить корабли")
            }

            return
        }

        /**
         * ---------------------------------------------------------------------------------------------
         * Основной игровой режим.
         *
         * currentTurn определяет игрока, который должен выполнить
         * следующий выстрел.
         * ---------------------------------------------------------------------------------------------
         */
        val currentPlayer = game.currentTurn

        var coordText by remember {
            mutableStateOf("")
        }

        var moveError by remember {
            mutableStateOf<String?>(null)
        }

        Text(
            "Ход: ${currentPlayer.name}",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        /**
         * ---------------------------------------------------------------------------------------------
         * Поле ввода координаты и кнопка выстрела.
         * ---------------------------------------------------------------------------------------------
         */
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            OutlinedTextField(
                value = coordText,
                onValueChange = {

                    coordText = it
                    moveError = null
                },
                label = {
                    Text("Координата (напр. A5)")
                },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Spacer(
                modifier = Modifier.width(8.dp)
            )

            Button(
                onClick = {

                    val coord =
                        Coordinate.fromString(coordText)

                    if (coord == null) {

                        moveError = "Неверный формат"

                        return@Button
                    }

                    moveError =
                        ctrl.makeMove(coord)

                    /**
                     * После хода партия могла измениться:
                     *
                     * - изменился список ходов;
                     * - сменился текущий игрок;
                     * - корабль мог быть потоплен;
                     * - игра могла завершиться.
                     *
                     * Поэтому принудительно обновляем интерфейс.
                     */
                    onRefresh()

                    if (moveError == null) {
                        coordText = ""
                    }
                }
            ) {
                Text("Выстрел")
            }
        }

        /**
         * Отображение ошибки выполнения хода.
         */
        moveError?.let {

            Text(
                it,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            "История ходов:",
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        /**
         * ---------------------------------------------------------------------------------------------
         * История всех совершённых ходов.
         *
         * Лог занимает всё оставшееся место окна и становится
         * прокручиваемым при большом количестве ходов.
         * ---------------------------------------------------------------------------------------------
         */
        Box(
            modifier = Modifier.weight(1f)
        ) {

            Column(
                modifier = Modifier.verticalScroll(
                    rememberScrollState()
                )
            ) {

                for (move in game.moves) {

                    val icon =
                        when (move.result) {

                            ShotResult.MISS ->
                                "· Промах"

                            ShotResult.HIT ->
                                "X Попадание"

                            ShotResult.SUNK ->
                                "💀 Потоплен"

                            ShotResult.WIN ->
                                "🏆 Победа"
                        }

                    Text(
                        "#${move.turnNumber}  " +
                                "${move.player.name.padEnd(10)} → " +
                                "${move.coordinate.toDisplayString().padEnd(4)} — " +
                                icon
                    )
                }
            }
        }
    }
}
