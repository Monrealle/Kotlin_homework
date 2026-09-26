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
import battleship.application.PlacementParser
import battleship.application.RandomShipPlacer
import battleship.domain.model.*
import battleship.presentation.console.GameHistoryFormatter

/**
 * =============================================================================================
 * Главное Compose-приложение администратора «Морского боя».
 *
 * Интерфейс разделён на три вкладки:
 *
 * 1. «Игроки» - создание игроков и просмотр их статистики.
 * 2. «Новая партия» - выбор двух игроков и запуск новой партии.
 * 3. «История партий» - просмотр всех партий и подробностей каждой партии.
 *
 * В отличие от старой реализации GUI расстановка кораблей теперь выполняется
 * в первую очередь вручную. Автоматическая расстановка оставлена дополнительной кнопкой.
 *
 * @param ctrl контроллер GUI, через который выполняются операции приложения
 * =============================================================================================
 */
@Composable
fun AdminApp(ctrl: GuiController) {

    /* Индекс текущей вкладки. */
    var tab by remember { mutableStateOf(0) }

    /* Определяет, нужно ли показывать текущую партию внутри вкладки истории. */
    var showCurrentGame by remember { mutableStateOf(false) }

    /**
     * ---------------------------------------------------------------------------------------------
     * Версия списка истории партий.
     *
     * Увеличение значения заставляет вкладку истории перечитать данные из репозитория.
     * ---------------------------------------------------------------------------------------------
     */
    var historyRevision by remember { mutableIntStateOf(0) }

    /* Список игроков, отображаемый в интерфейсе. */
    val players = remember { mutableStateListOf<Player>() }

    /* Загружает существующих игроков при первом отображении приложения. */
    LaunchedEffect(Unit) {
        players.clear()
        players.addAll(ctrl.playerRepo.findAll())
    }

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
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
                    onClick = {
                        tab = 2
                        showCurrentGame = false
                    }
                ) {
                    Text("История партий")
                }
            }

            /* Отображение содержимого выбранной вкладки. */
            when (tab) {
                0 -> PlayersTab(
                    ctrl = ctrl,
                    players = players
                )

                1 -> NewGameTab(
                    ctrl = ctrl,
                    players = players,
                    onStart = {
                        historyRevision++
                        showCurrentGame = true
                        tab = 2
                    }
                )

                2 -> HistoryTab(
                    ctrl = ctrl,
                    showCurrentGame = showCurrentGame,
                    historyRevision = historyRevision,
                    onOpenCurrentGame = { showCurrentGame = true },
                    onShowHistory = {
                        showCurrentGame = false
                        historyRevision++
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
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
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
                label = { Text("Имя") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    error = ctrl.addPlayer(name)

                    if (error == null) {
                        players.clear()
                        players.addAll(ctrl.playerRepo.findAll())
                        name = ""
                    }
                }
            ) {
                Text("Добавить")
            }
        }

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Игроки",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (players.isEmpty()) {
                    Text("Игроков пока нет.")
                }

                players.forEach { player ->
                    val stats = ctrl.getStats(player)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                player.name,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Рейтинг: ${stats.currentElo} | " +
                                        "Игр: ${stats.gamesPlayed} | " +
                                        "Побед: ${stats.wins} | " +
                                        "Винрейт: ${"%.1f".format(stats.winRate * 100)}%"
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * =============================================================================================
 * Вкладка создания новой партии.
 *
 * Позволяет выбрать двух разных игроков и создать новую партию.
 * Расстановка кораблей выполняется после создания партии на отдельном экране.
 * =============================================================================================
 */
@Composable
fun NewGameTab(
    ctrl: GuiController,
    players: List<Player>,
    onStart: () -> Unit
) {
    var p1 by remember { mutableStateOf<Player?>(null) }
    var p2 by remember { mutableStateOf<Player?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Создание партии",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Игрок 1",
            fontWeight = FontWeight.Bold
        )

        players.forEach { player ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = p1 == player,
                    onClick = {
                        p1 = player
                        if (p2 == player) p2 = null
                        error = null
                    }
                )
                Text(player.name)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Игрок 2",
            fontWeight = FontWeight.Bold
        )

        players.forEach { player ->
            if (player != p1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = p2 == player,
                        onClick = {
                            p2 = player
                            error = null
                        }
                    )
                    Text(player.name)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val first = p1
                val second = p2

                if (first == null || second == null) {
                    error = "Выберите обоих игроков"
                    return@Button
                }

                error = ctrl.startGame(first, second)

                if (error == null) {
                    onStart()
                }
            }
        ) {
            Text("Создать партию")
        }

        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * =============================================================================================
 * Вкладка истории партий.
 *
 * Для каждой сохранённой партии доступны подробности:
 *
 * - все ходы;
 * - расстановка кораблей обоих игроков;
 * - результат партии;
 * - изменения рейтинга Эло.
 *
 * Подробный текст отчёта формируется тем же [GameHistoryFormatter],
 * который используется консольной версией приложения.
 * =============================================================================================
 */
@Composable
fun HistoryTab(
    ctrl: GuiController,
    showCurrentGame: Boolean,
    historyRevision: Int,
    onOpenCurrentGame: () -> Unit,
    onShowHistory: () -> Unit
) {
    if (showCurrentGame && ctrl.game != null) {
        GameTab(
            ctrl = ctrl,
            onBack = onShowHistory
        )
        return
    }

    val games = remember(historyRevision) {
        ctrl.getGameHistory()
    }

    var selectedGameId by remember(historyRevision) {
        mutableStateOf<String?>(null)
    }

    selectedGameId?.let { id ->
        val selectedGame = games.firstOrNull { it.id == id }

        if (selectedGame != null) {
            HistoryDetails(
                game = selectedGame,
                onBack = { selectedGameId = null }
            )
            return
        }
    }

    val finishedCount = games.count { it.status == GameStatus.FINISHED }
    val abandonedCount = games.count { it.status == GameStatus.ABANDONED }
    val inProgressCount = games.count {
        it.status != GameStatus.FINISHED && it.status != GameStatus.ABANDONED
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "История партий",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                modifier = Modifier.weight(1f)
            )

            Button(onClick = onShowHistory) {
                Text("Обновить")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Всего партий: ${games.size} | " +
                    "В процессе: $inProgressCount | " +
                    "Завершено: $finishedCount | " +
                    "Прервано: $abandonedCount"
        )

        if (ctrl.game != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onOpenCurrentGame) {
                Text("Открыть текущую партию")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (games.isEmpty()) {
                    Text("Партий пока нет.")
                }

                games.forEach { game ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                "${game.player1.name} vs ${game.player2.name}",
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "Статус: ${statusText(game.status)} | " +
                                        "Ходов: ${game.moves.size} | " +
                                        "Победитель: ${game.winner?.name ?: "—"}"
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    selectedGameId = game.id
                                }
                            ) {
                                Text("Подробнее")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * =============================================================================================
 * Подробности одной исторической партии.
 *
 * Используется один и тот же формат отчёта, что и в консольной версии.
 * Поэтому GUI показывает все сведения, необходимые администратору для проверки партии.
 * =============================================================================================
 */
@Composable
private fun HistoryDetails(
    game: Game,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Партия #${game.id.take(8)}",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                modifier = Modifier.weight(1f)
            )

            Button(onClick = onBack) {
                Text("Назад")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "${game.player1.name} vs ${game.player2.name} | " +
                    statusText(game.status)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Text(
                GameHistoryFormatter.format(game),
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        }
    }
}

/**
 * =============================================================================================
 * Вкладка текущей партии.
 *
 * В зависимости от статуса показывает:
 *
 * - экран ручной или автоматической расстановки;
 * - администрирование ходов;
 * - полный отчёт после завершения партии.
 * =============================================================================================
 */
@Composable
fun GameTab(
    ctrl: GuiController,
    onBack: () -> Unit
) {
    var revision by remember { mutableIntStateOf(0) }
    val game = ctrl.game

    if (game == null) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Нет активной партии")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onBack) {
                Text("Вернуться к истории")
            }
        }
        return
    }

    key(revision) {
        GameContent(
            ctrl = ctrl,
            game = game,
            onRefresh = { revision++ },
            onBack = onBack
        )
    }
}

/**
 * =============================================================================================
 * Содержимое вкладки текущей партии.
 * =============================================================================================
 */
@Composable
private fun GameContent(
    ctrl: GuiController,
    game: Game,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    when (game.status) {
        GameStatus.SETUP_P1,
        GameStatus.SETUP_P2 -> PlacementContent(
            ctrl = ctrl,
            game = game,
            onRefresh = onRefresh,
            onBack = onBack
        )

        GameStatus.IN_PROGRESS -> PlayContent(
            ctrl = ctrl,
            game = game,
            onRefresh = onRefresh,
            onBack = onBack
        )

        GameStatus.FINISHED,
        GameStatus.ABANDONED -> FinishedGameContent(
            ctrl = ctrl,
            game = game,
            onBack = onBack
        )
    }
}

/**
 * =============================================================================================
 * Экран расстановки кораблей.
 *
 * Основной способ - ручной ввод по тому же стандарту, что используется в консоли:
 *
 * `A1-A4; C1-C3; ...; J10`
 *
 * Дополнительная кнопка «Авторасстановка» использует [RandomShipPlacer] и оставлена
 * только как удобный вспомогательный способ.
 * =============================================================================================
 */
@Composable
private fun PlacementContent(
    ctrl: GuiController,
    game: Game,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val currentPlayer = when (game.status) {
        GameStatus.SETUP_P1 -> game.player1
        GameStatus.SETUP_P2 -> game.player2
        else -> return
    }

    var placementText by remember(currentPlayer.id, game.status) {
        mutableStateOf("")
    }

    var error by remember(currentPlayer.id, game.status) {
        mutableStateOf<String?>(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Расстановка кораблей",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Сейчас расставляет: ${currentPlayer.name}",
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Введите 10 кораблей через ';'. Пример: " +
                    "A1-A4; C1-C3; E1-E3; G1-G2; I1-I2; G4-G5; A7; C7; E7; G7"
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = placementText,
            onValueChange = {
                placementText = it
                error = null
            },
            label = { Text("Расстановка") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val parsed = PlacementParser.parse(placementText)

                    if (parsed.isFailure) {
                        error = parsed.exceptionOrNull()?.message
                        return@Button
                    }

                    val ships = parsed.getOrThrow()
                    val result = ctrl.placeShips(
                        currentPlayer,
                        ships
                    )

                    if (result.isValid) {
                        placementText = ""
                        error = null
                        onRefresh()
                    } else {
                        error = result.errors.joinToString("; ")
                    }
                }
            ) {
                Text("Применить расстановку")
            }

            OutlinedButton(
                onClick = {
                    val result = ctrl.placeShips(
                        currentPlayer,
                        RandomShipPlacer.generate()
                    )

                    if (result.isValid) {
                        placementText = ""
                        error = null
                        onRefresh()
                    } else {
                        error = result.errors.joinToString("; ")
                    }
                }
            ) {
                Text("Авторасстановка")
            }
        }

        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Правила расстановки",
            fontWeight = FontWeight.Bold
        )
        Text("• ровно 10 кораблей: 1 линкор, 2 крейсера, 3 эсминца, 4 катера")
        Text("• корабли только горизонтальные или вертикальные")
        Text("• корабли не должны пересекаться и соприкасаться, в том числе по диагонали")

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                ctrl.finish()
                onBack()
            }
        ) {
            Text("Прервать партию")
        }
    }
}

/**
 * =============================================================================================
 * Экран администрирования ходов.
 *
 * Показывает текущего игрока, ввод координаты, историю ходов и обе расстановки кораблей.
 * =============================================================================================
 */
@Composable
private fun PlayContent(
    ctrl: GuiController,
    game: Game,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    var coordText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Ход: ${game.currentTurn.name}",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                modifier = Modifier.weight(1f)
            )

            OutlinedButton(
                onClick = {
                    ctrl.finish()
                    onBack()
                }
            ) {
                Text("Прервать партию")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = coordText,
                onValueChange = {
                    coordText = it
                    error = null
                },
                label = { Text("Координата, например A5") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    val coordinate = Coordinate.fromString(coordText)

                    if (coordinate == null) {
                        error = "Неверный формат координаты"
                        return@Button
                    }

                    error = ctrl.makeMove(coordinate)

                    if (error == null) {
                        coordText = ""
                        onRefresh()
                    }
                }
            ) {
                Text("Выстрел")
            }
        }

        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Расстановки кораблей",
            fontWeight = FontWeight.Bold
        )

        Text("${game.player1.name}: ${formatShipsCompact(game.board1.ships)}")
        Text("${game.player2.name}: ${formatShipsCompact(game.board2.ships)}")

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "История ходов",
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (game.moves.isEmpty()) {
                Text("Ходов пока нет.")
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    game.moves.forEach { move ->
                        Text(
                            "${move.turnNumber}. ${move.player.name}: " +
                                    "${move.coordinate.toDisplayString()} -> ${move.result}"
                        )
                    }
                }
            }
        }
    }
}

/**
 * =============================================================================================
 * Экран завершённой или прерванной партии.
 *
 * Полный отчёт совпадает с тем, который используется в консоли, поэтому здесь
 * присутствуют и ходы, и расстановки, и итоговые рейтинги.
 * =============================================================================================
 */
@Composable
private fun FinishedGameContent(
    ctrl: GuiController,
    game: Game,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            if (game.status == GameStatus.FINISHED) {
                "Партия завершена"
            } else {
                "Партия прервана"
            },
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Text(
                GameHistoryFormatter.format(game),
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                ctrl.finish()
                onBack()
            }
        ) {
            Text("Вернуться к истории")
        }
    }
}

/**
 * ---------------------------------------------------------------------------------------------
 * Формирует компактный текст расстановки кораблей для текущей партии.
 * ---------------------------------------------------------------------------------------------
 */
private fun formatShipsCompact(ships: List<Ship>): String =
    if (ships.isEmpty()) {
        "—"
    } else {
        ships.joinToString("; ") { ship ->
            "${ship.type.displayName}: " +
                    ship.segments.joinToString(" ") { it.toDisplayString() }
        }
    }

/**
 * ---------------------------------------------------------------------------------------------
 * Переводит внутренний статус партии в текст для интерфейса.
 * ---------------------------------------------------------------------------------------------
 */
private fun statusText(status: GameStatus): String = when (status) {
    GameStatus.SETUP_P1,
    GameStatus.SETUP_P2,
    GameStatus.IN_PROGRESS -> "В процессе"
    GameStatus.FINISHED -> "Завершена"
    GameStatus.ABANDONED -> "Прервана"
}
