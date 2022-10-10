package io.github.risu729.utilities_bot;

import com.google.common.collect.MoreCollectors;
import com.google.common.io.MoreFiles;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

enum BotUser {

  // must be declared at first
  ADMIN(event -> {
    Arrays.asList(values()).subList(1, values().length) // skip ADMIN
        .forEach(botUser -> botUser.onMessageReceived(event));
    var runtime = Runtime.getRuntime();
    long freeMemory = runtime.freeMemory();
    long totalMemory = runtime.totalMemory();
    event.getChannel()
        .sendMessage(
            "You are an admin!%nMemory Usage: %d MiB (Free: %d MiB, Total: %d MiB)".formatted(
                (totalMemory - freeMemory) / 1024 / 1024, freeMemory / 1024 / 1024,
                totalMemory / 1024 / 1024))
        .queue();
  }),

  FURY(event -> {
    // extract block names from attachments
    List<String> names = event.getMessage()
        .getAttachments()
        .stream()
        .filter(a -> a.getFileName().endsWith(".png") || a.getFileName().endsWith(".zip"))
        .mapMulti((Message.Attachment a, Consumer<String> consumer) -> {
          if (a.getFileName().endsWith(".png")) {
            consumer.accept(a.getFileName());
          } else if (a.getFileName().endsWith(".zip")) {
            try {
              var path = a.getProxy().downloadToPath(Bot.TEMP_DIR.resolve(a.getFileName())).get();
              try (var zip = new ZipFile(path.toFile())) {
                zip.getFileHeaders()
                    .stream()
                    .map(FileHeader::getFileName)
                    .filter(name -> name.endsWith(".png"))
                    .forEach(consumer);
              }
              Files.delete(path);
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            } catch (InterruptedException | ExecutionException e) {
              throw new RuntimeException(e);
            }
          }
        })
        .map(s -> s.substring(0, s.lastIndexOf('.')))
        .toList();
    if (names.isEmpty()) {
      return;
    }
    // generate json files and send them
    final String jsonTemplate = """
        {
            "format_version": "1.16.100",
            "minecraft:texture_set": {
                "color":"%s",
                "metalness_emissive_roughness": "#0000ff",
                "heightmap": "flat_heightmap"
            }
        }""";
    try {
      var tempDir = Files.createTempDirectory(Bot.TEMP_DIR, "fury");
      List<Path> files = new ArrayList<>();
      names.forEach(name -> {
        try {
          files.add(Files.writeString(tempDir.resolve(name + ".texture_set.json"),
              String.format(jsonTemplate, name)));
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
      if (names.size() <= Message.MAX_FILE_AMOUNT) {
        event.getChannel().sendFiles(files.stream().map(FileUpload::fromData).toList()).queue();
      }
      var path = Bot.TEMP_DIR.resolve(tempDir.getFileName() + ".zip");
      try (var zip = new ZipFile(path.toFile())) {
        zip.addFiles(files.stream().map(Path::toFile).toList());
      }
      event.getChannel().sendFiles(FileUpload.fromData(path)).queue();
      MoreFiles.deleteRecursively(tempDir);
      Files.delete(path);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  });

  private static final String ENV_SUFFIX = "_USER_IDS";

  private final List<Long> ids;
  private final @NotNull Consumer<? super @NotNull MessageReceivedEvent> onMessageReceived;

  BotUser(@NotNull Consumer<? super @NotNull MessageReceivedEvent> onMessageReceived) {
    this.ids = Arrays.stream(checkNotNull(System.getenv(name() + ENV_SUFFIX)).split(","))
        .map(Long::parseLong)
        .toList();
    this.onMessageReceived = onMessageReceived;
  }

  static @NotNull Optional<@NotNull BotUser> fromUser(@Nullable User user) {
    return Arrays.stream(values())
        .filter(botUser -> Optional.ofNullable(user)
            .map(User::getIdLong)
            .map(botUser.ids::contains)
            .orElse(false))
        .collect(MoreCollectors.toOptional());
  }

  void onMessageReceived(@NotNull MessageReceivedEvent event) {
    onMessageReceived.accept(event);
  }
}
