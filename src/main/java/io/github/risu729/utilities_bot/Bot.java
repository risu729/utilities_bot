/*
 * Copyright (c) 2022 Risu
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.github.risu729.utilities_bot;

import com.google.common.io.MoreFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.security.auth.login.LoginException;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

public final class Bot {

  static final String SERVICE_NAME = "Utilities Bot";
  static final Path TEMP_DIR = Path.of(System.getProperties().getProperty("java.io.tmpdir"))
      .resolve(SERVICE_NAME);

  private Bot() {
    throw new AssertionError();
  }

  public static void main(String[] args) {

    // start bot
    try {
      JDABuilder.createDefault(System.getenv("DISCORD_TOKEN"))
          .enableIntents(GatewayIntent.MESSAGE_CONTENT)
          .addEventListeners(new ListenerAdapter() {
            @Override
            public void onMessageReceived(MessageReceivedEvent event) {
              if (event.isFromType(ChannelType.PRIVATE)) {
                return;
              }
              BotUser.fromUser(event.getAuthor())
                  .ifPresent(user -> user.onMessageReceived(event));
            }
          })
          .build()
          .awaitReady();
    } catch (LoginException | InterruptedException e) {
      throw new RuntimeException(e);
    }

    // create tempDir
    try {
      Files.createDirectories(TEMP_DIR);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    // delete tempDir on exit
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        MoreFiles.deleteRecursively(TEMP_DIR);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }));
  }
}
