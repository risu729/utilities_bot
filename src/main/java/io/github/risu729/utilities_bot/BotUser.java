package io.github.risu729.utilities_bot;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.MoreCollectors;
import com.google.common.io.MoreFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

enum BotUser {
  ADMIN(event -> Arrays.asList(BotUser.values())
      .subList(1, BotUser.values().length) // skip ADMIN
      .forEach(botUser -> botUser.onMessageReceived(event))), // must be declared at first
  FURY(event -> {
    // extract block names from attachments
    List<String> names = event.getMessage().getAttachments().stream()
        .filter(a -> a.isImage() || Objects.equals(a.getFileExtension(), "zip"))
        .mapMulti((Attachment a, Consumer<String> consumer) -> {
          if (a.isImage()) {
            consumer.accept(a.getFileName());
          } else if (Objects.equals(a.getFileExtension(), "zip")) {
            try {
              var path = a.getProxy().downloadToPath(Bot.TEMP_DIR.resolve(a.getFileName())).get();
              try (var zip = new ZipFile(path.toFile())) {
                zip.getFileHeaders().stream()
                    .map(FileHeader::getFileName)
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
        event.getChannel().sendFiles(files.stream().map(FileUpload::fromData).toList())
            .queue();
      }
      var path = Bot.TEMP_DIR.resolve(tempDir.getFileName() + ".zip");
      try (var zip = new ZipFile(path.toFile())) {
        zip.addFolder(tempDir.toFile());
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
  private final Consumer<MessageReceivedEvent> onMessageReceived;

  BotUser(Consumer<MessageReceivedEvent> onMessageReceived) {
    this.ids = Arrays.stream(checkNotNull(System.getenv(name() + ENV_SUFFIX))
            .split(","))
        .map(Long::parseLong)
        .toList();
    this.onMessageReceived = onMessageReceived;
  }

  static Optional<BotUser> fromUser(User user) {
    return Arrays.stream(values())
        .filter(botUser -> botUser.ids.contains(user.getIdLong()))
        .collect(MoreCollectors.toOptional());
  }

  void onMessageReceived(MessageReceivedEvent event) {
    onMessageReceived.accept(event);
  }
}
