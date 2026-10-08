package ru.servermine.cities.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.servermine.cities.api.CityTreasuryCode;
import ru.servermine.cities.api.CitiesService;
import ru.servermine.cities.api.CityRole;
import ru.servermine.cities.api.CityDiplomacyRequest;
import ru.servermine.cities.api.CityTreatyType;
import ru.servermine.cities.api.CityDiplomacyCode;
import ru.servermine.cities.api.CityTreasuryTransferRequest;
import ru.servermine.cities.api.CityTreasuryTransferCode;
import ru.servermine.cities.api.CityStage;
import ru.servermine.cities.core.CityRepository;
import ru.servermine.cities.gui.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class CitiesAdminCommand implements CommandExecutor, TabCompleter {
    private final CitiesService cities;
    private final CityBookService books;
    private final MenuManager menus;
    private final JavaPlugin plugin;
    private final CityRepository repository;
    public CitiesAdminCommand(JavaPlugin plugin, CitiesService cities, CityBookService books, MenuManager menus,
                              CityRepository repository) {
        this.plugin = plugin; this.cities = cities; this.books = books; this.menus = menus; this.repository = repository;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("invite", "accept", "kick", "leave", "role", "treasury", "diplomacy", "buy", "select", "exit"));
            if (sender.hasPermission("servermine.cities.admin")) {
                options.addAll(List.of("status", "givebook", "open", "preview", "test"));
            }
            return matching(options, args[0]);
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("test") && sender.hasPermission("servermine.cities.admin")) {
            if (args.length == 2) return matching(List.of("treasury", "stage"), args[1]);
            if (args.length == 3) return onlinePlayers(args[2]);
            if (args.length == 4 && args[1].equalsIgnoreCase("stage"))
                return matching(java.util.Arrays.stream(CityStage.values()).map(Enum::name).toList(), args[3]);
            if (args.length == 4 && args[1].equalsIgnoreCase("treasury"))
                return matching(List.of("1000", "10000", "100000"), args[3]);
        }
        if (action.equals("diplomacy")) {
            if (args.length == 2) return matching(List.of("list", "propose", "accept", "decline", "cancel", "end", "gui"), args[1]);
            if (args.length == 3 && args[1].equalsIgnoreCase("propose"))
                return matching(List.of("alliance", "peace", "trade"), args[2]);
            if (args.length == 4 && args[1].equalsIgnoreCase("propose"))
                return matching(List.of("7", "30", "90"), args[3]);
            return List.of();
        }
        if (action.equals("role") && sender instanceof Player) {
            if (args.length == 2) return onlinePlayers(args[1]);
            if (args.length == 3) return matching(List.of("deputy", "treasurer", "builder", "member"), args[2]);
            return List.of();
        }
        if (action.equals("treasury") && sender instanceof Player) {
            if (args.length == 2) return matching(List.of("deposit", "history", "transfer"), args[1]);
            if (args.length == 3 && args[1].equalsIgnoreCase("deposit")) {
                return matching(List.of("100", "250", "500", "750", "1000"), args[2]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("transfer"))
                return matching(List.of("100", "250", "500", "1000"), args[2]);
            return List.of();
        }

        if (action.equals("open") || action.equals("preview")) {
            if (!sender.hasPermission("servermine.cities.admin")) return List.of();
            if (args.length == 2) return onlinePlayers(args[1]);
            if (args.length == 3) {
                return matching(List.of("MAIN", "TERRITORY", "UPGRADES", "TREASURY", "RESIDENTS", "MANAGEMENT", "DIPLOMACY"), args[2]);
            }
        }

        if (action.equals("givebook") || action.equals("invite") || action.equals("kick")) {
            if ((action.equals("givebook") && !sender.hasPermission("servermine.cities.admin"))
                    || (!(sender instanceof Player) && !sender.hasPermission("servermine.cities.admin"))) {
                return List.of();
            }
            if (args.length == 2) return onlinePlayers(args[1]);
        }
        return List.of();
    }

    private List<String> onlinePlayers(String prefix) {
        return matching(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), prefix);
    }

    private List<String> matching(List<String> options, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }

    private boolean isSecurityError(Throwable error) {
        while (error != null) {
            if (error instanceof SecurityException) return true;
            error = error.getCause();
        }
        return false;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("test")) {
            handleTestCommand(sender, args);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("diplomacy")) {
            handleDiplomacy(sender, args);
            return true;
        }
        if (args.length > 0 && (args[0].equalsIgnoreCase("buy") || args[0].equalsIgnoreCase("select")
                || args[0].equalsIgnoreCase("exit"))) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Эта команда доступна только игроку."); return true; }
            if (args.length != 1) { player.sendMessage("Использование: /smcities buy | /smcities exit"); return true; }
            if (args[0].equalsIgnoreCase("buy") || args[0].equalsIgnoreCase("select")) {
                if (!menus.territoryOverview().select(player)) player.sendMessage("Вы не находитесь в режиме обзора территории.");
            } else if (!menus.territoryOverview().exit(player, true)) {
                player.sendMessage("Вы не находитесь в режиме обзора территории.");
            }
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("role")) {
            if (args.length != 3) { sender.sendMessage("Использование: /smcities role <игрок> <deputy|treasurer|builder|member>"); return true; }
            if (!(sender instanceof Player player)) { sender.sendMessage("Эта команда доступна только игроку."); return true; }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { player.sendMessage("Игрок должен быть онлайн."); return true; }
            CityRole role;
            try { role = CityRole.valueOf(args[2].toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException invalid) { player.sendMessage("Роль: deputy, treasurer, builder или member."); return true; }
            if (role == CityRole.RULER) { player.sendMessage("Передать руководство можно только при выходе правителя."); return true; }
            var roleService = cities.roleService().orElse(null);
            if (roleService == null || !roleService.isReady()) { player.sendMessage("Управление ролями временно недоступно."); return true; }
            cities.cityForPlayer(player.getUniqueId()).whenComplete((city, lookupError) ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        if (lookupError != null || city == null || city.isEmpty()) { player.sendMessage("Вы не состоите в городе."); return; }
                        roleService.assign(player.getUniqueId(), target.getUniqueId(), role, city.get().revision())
                                .whenComplete((result, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    if (!player.isOnline()) return;
                                    if (error != null || result == null) { player.sendMessage("Не удалось изменить роль."); return; }
                                    switch (result.code()) {
                                        case ALREADY_ASSIGNED -> player.sendMessage("У игрока уже эта должность.");
                                        case ROLE_CHANGED -> {
                                            player.sendMessage("Роль игрока " + target.getName() + " изменена: " + role.displayName() + ".");
                                            if (target.isOnline()) target.sendMessage("Ваша роль в городе изменена: " + role.displayName() + ".");
                                        }
                                        case NO_CITY -> player.sendMessage("Вы не состоите в городе.");
                                        case PERMISSION_DENIED -> player.sendMessage("У вас нет права назначить эту роль или изменить этого участника.");
                                        case TARGET_NOT_IN_CITY -> player.sendMessage("Игрок не состоит в вашем городе.");
                                        case CANNOT_CHANGE_RULER -> player.sendMessage("Роль правителя меняется только при передаче руководства.");
                                        case STALE_CITY -> player.sendMessage("Состав города изменился. Повторите команду.");
                                        case SERVICE_UNAVAILABLE -> player.sendMessage("Управление ролями временно недоступно.");
                                        case INTERNAL_ERROR -> player.sendMessage("Не удалось изменить роль.");
                                    }
                                }));
                    }));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("treasury")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Эта команда доступна только игроку."); return true; }
            var treasury = cities.treasuryService().orElse(null);
            if (treasury == null || !treasury.isReady()) { player.sendMessage("Казна города восстанавливает операции или временно недоступна."); return true; }
            if (args.length == 3 && args[1].equalsIgnoreCase("deposit")) {
                long amount;
                try { amount = Long.parseLong(args[2]); }
                catch (NumberFormatException invalid) { player.sendMessage("Укажите целую сумму монет."); return true; }
                if (amount <= 0) { player.sendMessage("Сумма должна быть больше нуля."); return true; }
                treasury.deposit(player.getUniqueId(), amount).whenComplete((result, error) ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) return;
                            if (error != null || result == null) { player.sendMessage("Не удалось пополнить казну."); return; }
                            switch (result.code()) {
                                case DEPOSITED -> player.sendMessage("В казну внесено " + amount + " монет. Новый баланс: "
                                        + result.city().orElseThrow().treasury() + " монет.");
                                case NO_CITY -> player.sendMessage("Вы не состоите в городе.");
                                case INVALID_AMOUNT -> player.sendMessage("Сумма должна быть больше нуля.");
                                case INSUFFICIENT_FUNDS -> player.sendMessage("У вас недостаточно физических монет.");
                                case ECONOMY_UNAVAILABLE -> player.sendMessage("Экономика временно недоступна.");
                                case OPERATION_IN_PROGRESS, UNKNOWN_OUTCOME -> player.sendMessage("Операция ещё сверяется. Не повторяйте платёж; после перезапуска она будет восстановлена.");
                                case SERVICE_UNAVAILABLE -> player.sendMessage("Казна города временно недоступна.");
                                case INTERNAL_ERROR -> player.sendMessage("Не удалось пополнить казну.");
                            }
                        }));
                return true;
            }
            if (args.length == 2 && args[1].equalsIgnoreCase("history")) {
                treasury.history(player.getUniqueId(), 10).whenComplete((entries, error) ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) return;
                            if (isSecurityError(error)) { player.sendMessage("Историю казны могут смотреть Казначей, Заместитель и Правитель."); return; }
                            if (error != null) { player.sendMessage("Не удалось загрузить историю казны."); return; }
                            if (entries.isEmpty()) { player.sendMessage("Операций в казне пока нет."); return; }
                            player.sendMessage("Последние операции казны:");
                            entries.forEach(entry -> player.sendMessage((entry.amount() > 0 ? "+" : "−")
                                    + Math.abs(entry.amount()) + " монет; баланс " + entry.balanceAfter()
                                    + "; " + entry.reason() + "; " + entry.occurredAt()));
                        }));
                return true;
            }
            if (args.length >= 4 && args[1].equalsIgnoreCase("transfer")) {
                long amount;
                try { amount = Long.parseLong(args[2]); }
                catch (NumberFormatException invalid) { player.sendMessage("Укажите целую сумму монет."); return true; }
                if (amount <= 0) { player.sendMessage("Сумма должна быть больше нуля."); return true; }
                String targetName = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
                var diplomacy = cities.diplomacyService().orElse(null);
                if (diplomacy == null || !diplomacy.isReady()) { player.sendMessage("Дипломатия сейчас недоступна."); return true; }
                diplomacy.snapshot(player.getUniqueId()).whenComplete((snapshot, lookupError) ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) return;
                            var target = lookupError == null && snapshot != null && snapshot.isPresent()
                                    ? snapshot.get().otherCities().stream().filter(city -> city.name().equalsIgnoreCase(targetName)).findFirst().orElse(null)
                                    : null;
                            if (target == null) { player.sendMessage("Город не найден. Укажите его точное название и проверьте, что есть действующий торговый договор."); return; }
                            treasury.transfer(new CityTreasuryTransferRequest(UUID.randomUUID(), player.getUniqueId(), target.id(), amount))
                                    .whenComplete((result, transferError) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                                        if (!player.isOnline()) return;
                                        if (transferError != null || result == null) { player.sendMessage("Не удалось выполнить перевод. Проверьте казны перед повтором."); return; }
                                        if (result.code() == CityTreasuryTransferCode.SENT || result.code() == CityTreasuryTransferCode.REPLAYED) {
                                            player.sendMessage("Переведено " + amount + " монет в казну города «" + result.targetCity().map(ru.servermine.cities.api.CityView::name).orElse(target.name()) + "».");
                                        } else player.sendMessage(transferMessage(result.code()));
                                    }));
                        }));
                return true;
            }
            player.sendMessage("Использование: /smcities treasury deposit <сумма> | history | transfer <сумма> <город>");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("invite")) {
            if (args.length != 2) { sender.sendMessage("Использование: /smcities invite <игрок>"); return true; }
            if (!(sender instanceof Player player)) { sender.sendMessage("Эта команда доступна только игроку."); return true; }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { player.sendMessage("Игрок должен быть онлайн."); return true; }
            var membership = cities.membershipService().orElse(null);
            if (membership == null || !membership.isReady()) { player.sendMessage("Управление составом города временно недоступно."); return true; }
            membership.invite(player.getUniqueId(), target.getUniqueId(), target.getName()).whenComplete((result, error) ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        if (error != null || result == null) { player.sendMessage("Не удалось отправить приглашение."); return; }
                        switch (result.code()) {
                            case INVITED -> {
                                String cityName = result.city().map(ru.servermine.cities.api.CityView::name).orElse("город");
                                player.sendMessage("Приглашение в город " + cityName + " отправлено игроку " + target.getName() + ". Оно действует 48 часов.");
                                if (target.isOnline()) target.sendMessage("Вас пригласили в город " + cityName + ". Примите приглашение командой /smcities accept.");
                            }
                            case NO_CITY -> player.sendMessage("Вы не состоите в городе.");
                            case PERMISSION_DENIED -> player.sendMessage("У вашей должности нет права приглашать игроков.");
                            case TARGET_ALREADY_IN_CITY -> player.sendMessage("Этот игрок уже состоит в городе.");
                            case NOT_INVITED, ALREADY_IN_CITY, ACCEPTED -> player.sendMessage("Не удалось отправить приглашение.");
                            case SERVICE_UNAVAILABLE -> player.sendMessage("Управление составом города временно недоступно.");
                            case INTERNAL_ERROR -> player.sendMessage("Не удалось отправить приглашение.");
                        }
                    }));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("accept")) {
            if (args.length != 1) { sender.sendMessage("Использование: /smcities accept"); return true; }
            if (!(sender instanceof Player player)) { sender.sendMessage("Эта команда доступна только игроку."); return true; }
            var membership = cities.membershipService().orElse(null);
            if (membership == null || !membership.isReady()) { player.sendMessage("Управление составом города временно недоступно."); return true; }
            membership.accept(player.getUniqueId(), player.getName()).whenComplete((result, error) ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        if (error != null || result == null) { player.sendMessage("Не удалось принять приглашение."); return; }
                        switch (result.code()) {
                            case ACCEPTED -> player.sendMessage("Вы вступили в город " + result.city().map(ru.servermine.cities.api.CityView::name).orElse("") + ".");
                            case NOT_INVITED -> player.sendMessage("У вас нет действующего приглашения.");
                            case ALREADY_IN_CITY -> player.sendMessage("Вы уже состоите в городе.");
                            case SERVICE_UNAVAILABLE -> player.sendMessage("Управление составом города временно недоступно.");
                            case NO_CITY, PERMISSION_DENIED, TARGET_ALREADY_IN_CITY, INVITED, INTERNAL_ERROR -> player.sendMessage("Не удалось принять приглашение.");
                        }
                    }));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("kick")) {
            if (args.length != 2) { sender.sendMessage("Использование: /smcities kick <игрок>"); return true; }
            if (!(sender instanceof Player player)) { sender.sendMessage("Эта команда доступна только игроку."); return true; }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { player.sendMessage("Игрок должен быть онлайн."); return true; }
            var membership = cities.membershipService().orElse(null);
            if (membership == null || !membership.isReady()) { player.sendMessage("Управление составом города временно недоступно."); return true; }
            cities.cityForPlayer(player.getUniqueId()).whenComplete((city, lookupError) ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        if (lookupError != null || city == null || city.isEmpty()) { player.sendMessage("Вы не состоите в городе."); return; }
                        membership.kick(player.getUniqueId(), target.getUniqueId(), city.get().revision()).whenComplete((result, error) ->
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    if (!player.isOnline()) return;
                                    if (error != null || result == null) { player.sendMessage("Не удалось исключить игрока."); return; }
                                    switch (result.code()) {
                                        case KICKED -> {
                                            player.sendMessage(target.getName() + " исключён из города.");
                                            if (target.isOnline()) target.sendMessage("Вас исключили из города " + city.get().name() + ".");
                                        }
                                        case PERMISSION_DENIED -> player.sendMessage("У вашей должности нет права исключить этого участника.");
                                        case TARGET_NOT_IN_CITY -> player.sendMessage("Игрок не состоит в вашем городе.");
                                        case SELF_TARGET -> player.sendMessage("Нельзя исключить себя. Используйте /smcities leave.");
                                        case CANNOT_KICK_RULER -> player.sendMessage("Нельзя исключить правителя города.");
                                        case STALE_CITY -> player.sendMessage("Состав изменился. Повторите команду.");
                                        default -> player.sendMessage("Не удалось исключить игрока.");
                                    }
                                }));
                    }));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("leave")) {
            if (args.length != 1) { sender.sendMessage("Использование: /smcities leave"); return true; }
            if (!(sender instanceof Player player)) { sender.sendMessage("Эта команда доступна только игроку."); return true; }
            var membership = cities.membershipService().orElse(null);
            if (membership == null || !membership.isReady()) { player.sendMessage("Управление составом города пока недоступно."); return true; }
            membership.leave(player.getUniqueId()).whenComplete((result, error) ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        if (error != null || result == null) { player.sendMessage("Не удалось выйти из города. Попробуйте позже."); return; }
                        switch (result.code()) {
                            case LEFT -> {
                                player.sendMessage("Вы покинули город.");
                                result.newRulerId().ifPresent(newRuler -> result.city().ifPresent(city -> city.residents().stream()
                                        .filter(resident -> resident.playerId().equals(newRuler)).findFirst()
                                        .ifPresent(resident -> player.sendMessage("Правителем автоматически стал " + resident.name() + "."))));
                            }
                            case NOT_IN_CITY -> player.sendMessage("Вы не состоите в городе.");
                            case LAST_RULER -> player.sendMessage("Нельзя покинуть город, если вы его последний правитель. Сначала пригласите преемника.");
                            case SERVICE_UNAVAILABLE -> player.sendMessage("Управление составом города временно недоступно.");
                            case INTERNAL_ERROR -> player.sendMessage("Не удалось выйти из города. Попробуйте позже.");
                        }
                    }));
            return true;
        }
        if (!sender.hasPermission("servermine.cities.admin")) { sender.sendMessage("Нет административного права."); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("ServerMineCities: storage ready=" + cities.isReady()
                    + "; founding ready=" + cities.foundingService().filter(ru.servermine.cities.api.CityFoundingService::isReady).isPresent()
                    + "; progression ready=" + cities.progressionService().filter(ru.servermine.cities.api.CityProgressionService::isReady).isPresent()
                    + "; territory ready=" + cities.territoryService().filter(ru.servermine.cities.api.CityTerritoryService::isReady).isPresent()
                    + "; members ready=" + cities.membershipService().filter(ru.servermine.cities.api.CityMembershipService::isReady).isPresent()
                    + "; treasury ready=" + cities.treasuryService().filter(ru.servermine.cities.api.CityTreasuryService::isReady).isPresent()
                    + "; roles ready=" + cities.roleService().filter(ru.servermine.cities.api.CityRoleService::isReady).isPresent()
                    + "; laws ready=" + cities.lawsService().filter(ru.servermine.cities.api.CityLawsService::isReady).isPresent()
                    + "; taxes ready=" + cities.taxService().filter(ru.servermine.cities.api.CityTaxService::isReady).isPresent());
            sender.sendMessage("/smcities <givebook|open|preview> <игрок> [menu]; /smcities invite <игрок>; /smcities accept; /smcities kick <игрок>; /smcities role <игрок> <роль>; /smcities leave");
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (!List.of("givebook", "open", "preview").contains(action)) return false;
        Player player = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
        if (player == null) { sender.sendMessage("Укажите онлайн-игрока."); return true; }
        if (action.equals("givebook")) {
            if (player.getInventory().firstEmpty() < 0) { sender.sendMessage("Инвентарь заполнен."); return true; }
            player.getInventory().addItem(books.createBook());
            sender.sendMessage("Книга выдана.");
            return true;
        }
        MenuType menu = MenuType.MAIN;
        if (args.length > 2) {
            try { menu = MenuType.valueOf(args[2].toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { sender.sendMessage("Неизвестное меню."); return true; }
        }
        if (action.equals("preview")) {
            menus.openPreview(player, menu);
            sender.sendMessage("Открыт демонстрационный макет. Покупки и изменения отключены.");
        } else menus.open(player, menu);
        return true;
    }

    private void handleTestCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("servermine.cities.admin")) {
            sender.sendMessage("Нет административного права servermine.cities.admin."); return;
        }
        if (args.length != 4 || (!args[1].equalsIgnoreCase("treasury") && !args[1].equalsIgnoreCase("stage"))) {
            sender.sendMessage("Использование: /smcities test treasury <игрок> <сумма> | /smcities test stage <игрок> <SETTLEMENT|VILLAGE|CITY|CAPITAL|KINGDOM>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) { sender.sendMessage("Игрок должен быть в сети."); return; }
        UUID operationId = UUID.randomUUID();
        UUID actorId = sender instanceof Player p ? p.getUniqueId()
                : UUID.nameUUIDFromBytes("server-console".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (args[1].equalsIgnoreCase("treasury")) {
            long amount;
            try { amount = Long.parseLong(args[3]); }
            catch (NumberFormatException invalid) { sender.sendMessage("Сумма должна быть целым числом от 1 до 1000000000."); return; }
            if (amount < 1 || amount > 1_000_000_000L) { sender.sendMessage("Сумма должна быть от 1 до 1000000000."); return; }
            repository.adminTestGrantTreasury(operationId, actorId, target.getUniqueId(), amount)
                    .whenComplete((city, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (error != null) { sender.sendMessage("Не удалось пополнить казну для теста."); plugin.getLogger().warning("Admin test treasury grant failed: " + error); return; }
                        if (city.isEmpty()) { sender.sendMessage("Игрок не состоит в городе."); return; }
                        sender.sendMessage("Тестовое пополнение: «" + city.get().name() + "» +" + amount + "; баланс " + city.get().treasury() + ".");
                        plugin.getLogger().info("Admin test treasury grant: actor=" + sender.getName() + ", target=" + target.getName() + ", city=" + city.get().name() + ", amount=" + amount);
                    }));
            return;
        }
        CityStage stage;
        try { stage = CityStage.valueOf(args[3].toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { sender.sendMessage("Этап: SETTLEMENT, VILLAGE, CITY, CAPITAL или KINGDOM."); return; }
        repository.adminTestSetStage(operationId, actorId, target.getUniqueId(), stage)
                .whenComplete((city, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (error != null) { sender.sendMessage("Не удалось изменить этап города для теста."); plugin.getLogger().warning("Admin test stage update failed: " + error); return; }
                    if (city.isEmpty()) { sender.sendMessage("Игрок не состоит в городе."); return; }
                    sender.sendMessage("Этап города «" + city.get().name() + "» установлен: " + stage.displayName() + ".");
                    plugin.getLogger().info("Admin test stage set: actor=" + sender.getName() + ", target=" + target.getName() + ", city=" + city.get().name() + ", stage=" + stage.name());
                }));
    }

    private String transferMessage(CityTreasuryTransferCode code) {
        return switch (code) {
            case NO_CITY, NOT_MEMBER -> "Вы не состоите в городе.";
            case PERMISSION_DENIED -> "Переводить средства могут Правитель, Заместитель и Казначей.";
            case INVALID_TARGET -> "Город-получатель недоступен.";
            case INVALID_AMOUNT -> "Сумма должна быть положительной.";
            case LIMIT_EXCEEDED -> "Сумма превышает лимит одного перевода.";
            case TRADE_REQUIRED -> "Для перевода нужен действующий торговый договор.";
            case INSUFFICIENT_TREASURY -> "В казне вашего города недостаточно монет.";
            case OPERATION_CONFLICT -> "Конфликт операции. Обратитесь к администратору.";
            case SERVICE_UNAVAILABLE, INTERNAL_ERROR -> "Сервис казны временно недоступен.";
            case SENT, REPLAYED -> "Перевод выполнен.";
        };
    }

    private void handleDiplomacy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Дипломатия доступна только игроку."); return; }
        var service = cities.diplomacyService().orElse(null);
        if (service == null || !service.isReady()) { player.sendMessage("Дипломатия временно недоступна."); return; }
        if (args.length == 1 || (args.length == 2 && args[1].equalsIgnoreCase("gui"))) {
            menus.open(player, MenuType.DIPLOMACY); return;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("list")) {
            service.snapshot(player.getUniqueId()).whenComplete((snapshot, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (error != null || snapshot == null || snapshot.isEmpty()) { player.sendMessage("Вы не состоите в городе или не удалось загрузить договоры."); return; }
                if (snapshot.get().treaties().isEmpty()) { player.sendMessage("Договоров и предложений пока нет."); return; }
                player.sendMessage("Договоры города (UUID нужен для команд принятия/отмены):");
                snapshot.get().treaties().forEach(t -> {
                    String other = snapshot.get().otherCities().stream().filter(c -> c.id().equals(t.proposingCityId()) || c.id().equals(t.targetCityId()))
                            .map(ru.servermine.cities.api.CityDiplomacySnapshot.CitySummary::name).findFirst().orElse("ваш город");
                    player.sendMessage(t.id() + " · " + t.type().displayName() + " · " + other + " · " + t.state());
                });
            }));
            return;
        }
        var operation = switch (args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "") {
            case "accept" -> CityDiplomacyRequest.Action.ACCEPT;
            case "decline" -> CityDiplomacyRequest.Action.DECLINE;
            case "cancel" -> CityDiplomacyRequest.Action.CANCEL;
            case "end" -> CityDiplomacyRequest.Action.TERMINATE;
            default -> null;
        };
        if (operation != null && args.length == 3) {
            UUID treatyId;
            try { treatyId = UUID.fromString(args[2]); }
            catch (IllegalArgumentException invalid) { player.sendMessage("Укажите UUID договора из /smcities diplomacy list."); return; }
            executeDiplomacy(player, service, null, operation, treatyId, null, null, 30);
            return;
        }
        if (args.length >= 5 && args[1].equalsIgnoreCase("propose")) {
            CityTreatyType type = switch (args[2].toLowerCase(Locale.ROOT)) {
                case "alliance" -> CityTreatyType.ALLIANCE; case "peace" -> CityTreatyType.NON_AGGRESSION;
                case "trade" -> CityTreatyType.TRADE; default -> null;
            };
            if (type == null) { player.sendMessage("Тип: alliance, peace или trade."); return; }
            int days = 30;
            try { days = Integer.parseInt(args[3]); } catch (NumberFormatException invalid) { days = -1; }
            if (days != 7 && days != 30 && days != 90) { player.sendMessage("Срок: 7, 30 или 90 дней."); return; }
            String targetName = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));
            int duration = days;
            service.snapshot(player.getUniqueId()).whenComplete((snapshot, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (error != null || snapshot == null || snapshot.isEmpty()) { player.sendMessage("Вы не состоите в городе."); return; }
                var target = snapshot.get().otherCities().stream().filter(c -> c.name().equalsIgnoreCase(targetName)).findFirst().orElse(null);
                if (target == null) { player.sendMessage("Город не найден. Название должно совпадать полностью."); return; }
                cities.cityForPlayer(player.getUniqueId()).whenComplete((city, cityError) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (cityError != null || city == null || city.isEmpty()) { player.sendMessage("Вы не состоите в городе."); return; }
                    executeDiplomacy(player, service, city.get().id(), CityDiplomacyRequest.Action.PROPOSE,
                            UUID.randomUUID(), target.id(), type, duration);
                }));
            }));
            return;
        }
        player.sendMessage("Использование: /smcities diplomacy list | propose <alliance|peace|trade> <7|30|90> <город> | accept|decline|cancel|end <UUID> | gui");
    }

    private void executeDiplomacy(Player player, ru.servermine.cities.api.CityDiplomacyService service,
                                  java.util.UUID cityId, CityDiplomacyRequest.Action action,
                                  java.util.UUID treatyId, java.util.UUID targetCityId, CityTreatyType type) {
        executeDiplomacy(player, service, cityId, action, treatyId, targetCityId, type, 30);
    }

    private void executeDiplomacy(Player player, ru.servermine.cities.api.CityDiplomacyService service,
                                  java.util.UUID cityId, CityDiplomacyRequest.Action action,
                                  java.util.UUID treatyId, java.util.UUID targetCityId, CityTreatyType type, int days) {
        java.util.concurrent.CompletionStage<ru.servermine.cities.api.CityDiplomacyResult> result;
        if (cityId != null) {
            result = service.execute(new CityDiplomacyRequest(treatyId, cityId, player.getUniqueId(), action, targetCityId, type, days));
        } else {
            cities.cityForPlayer(player.getUniqueId()).thenCompose(city -> city.isEmpty()
                    ? java.util.concurrent.CompletableFuture.completedFuture(ru.servermine.cities.api.CityDiplomacyResult.of(CityDiplomacyCode.NOT_MEMBER))
                    : service.execute(new CityDiplomacyRequest(treatyId, city.get().id(), player.getUniqueId(), action, null, null, days)))
                    .whenComplete((r, e) -> plugin.getServer().getScheduler().runTask(plugin, () -> diplomacyResultMessage(player, r, e)));
            return;
        }
        result.whenComplete((r, e) -> plugin.getServer().getScheduler().runTask(plugin, () -> diplomacyResultMessage(player, r, e)));
    }

    private void diplomacyResultMessage(Player player, ru.servermine.cities.api.CityDiplomacyResult result, Throwable error) {
        if (!player.isOnline()) return;
        if (error != null || result == null) { player.sendMessage("Не удалось выполнить действие с договором."); return; }
        String message = switch (result.code()) {
            case CREATED -> "Предложение отправлено."; case ACCEPTED -> "Договор принят.";
            case DECLINED -> "Предложение отклонено."; case CANCELLED -> "Предложение отозвано.";
            case TERMINATED -> "Договор прекращён."; case PERMISSION_DENIED -> "Нужна должность заместителя или правителя.";
            case ALREADY_EXISTS -> "Такое соглашение уже существует."; case NOT_FOUND -> "Договор не найден.";
            case WRONG_SIDE -> "Действие доступно другой стороне."; default -> "Действие отклонено: " + result.code();
        };
        player.sendMessage(message);
    }
}
