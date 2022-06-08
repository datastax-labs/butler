/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jira.client;

import static com.datastax.butler.commons.jira.client.JiraException.error;
import static com.datastax.butler.commons.jira.client.JiraException.ioError;
import static java.lang.String.format;

import com.datastax.butler.commons.issues.jira.JiraIssueId;
import com.datastax.butler.commons.web.Email;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonReader.Token;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import okio.Buffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Converter between jira rest api json and jira.client pojo objects. */
public class JiraJsonConverter {
  private static final Logger logger = LogManager.getLogger();

  private final Moshi moshi;
  private final JsonAdapter<JiraObject> issueAdapter;
  private final JsonAdapter<JiraUser> userAdapter;
  private final JsonAdapter<IssuesPage> issuesPageAdapter;
  private final JsonAdapter<CreatedIssueResponse> createdIssueResponseJsonAdapter;

  public JiraJsonConverter() {
    this.moshi =
        new Moshi.Builder()
            .add(Email.class, adapter(Email::fromString))
            .add(URL.class, adapter(JiraJsonConverter::url))
            .add(LocalDateTime.class, new LocalDateTimeAdapter())
            .add(Instant.class, new InstantAdapter())
            .add(
                JiraIssueFields.Named.class,
                objectWithOneFieldAdapter("name", JiraIssueFields.Named::fromName))
            .build();
    this.issueAdapter = moshi.adapter(JiraObject.class);
    this.userAdapter = moshi.adapter(JiraUser.class);
    this.issuesPageAdapter = moshi.adapter(IssuesPage.class);
    this.createdIssueResponseJsonAdapter = moshi.adapter(CreatedIssueResponse.class);
  }

  private static URL url(String url) {
    try {
      return new URL(url);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(
          format(
              "URL %s is mal-formed%s", url, e.getMessage() == null ? "" : ": " + e.getMessage()));
    }
  }

  public JiraObject parseIssue(String json) {
    return parse(json, issueAdapter, "issue");
  }

  JiraUser parseUser(String json) {
    return parse(json, userAdapter, "user");
  }

  List<JiraUser> parseUsers(String json) {
    Type listUsers = Types.newParameterizedType(List.class, JiraUser.class);
    JsonAdapter<List<JiraUser>> usersAdapter = moshi.adapter(listUsers);
    return parse(json, usersAdapter, "users");
  }

  IssuesPage parseIssuesPage(String json) {
    return parse(json, issuesPageAdapter, "issues page");
  }

  JiraIssueId parseCreatedIssueResponse(String json) {
    String issueKey = parse(json, createdIssueResponseJsonAdapter, "created issue").key;
    return new JiraIssueId(issueKey);
  }

  private <T> T parse(String json, JsonAdapter<T> adapter, String what) {
    try {
      T parsed = adapter.fromJson(json);
      if (parsed == null) throw error("Error parsing JSON for %s (got null): %s", what, json);
      if (logger.isDebugEnabled()) {
        try (Buffer source = new Buffer().writeUtf8(json)) {
          JsonReader reader = JsonReader.of(source);
          Object value = reader.readJsonValue();
          String prettyPrinted = moshi.adapter(Object.class).indent("  ").toJson(value);
          logger.debug("Decoding JSON for {}:{}{}", what, System.lineSeparator(), prettyPrinted);
        }
      }
      return parsed;
    } catch (IOException e) {
      throw ioError(e, "Error parsing JSON for %s: %s", what, json);
    }
  }

  private static <T> JsonAdapter<T> adapter(Function<String, T> parser) {
    return new JsonAdapter<>() {
      @Nullable
      @Override
      public T fromJson(JsonReader reader) throws IOException {
        return reader.peek() == Token.NULL ? reader.nextNull() : parser.apply(reader.nextString());
      }

      @Override
      public void toJson(JsonWriter writer, @Nullable T value) throws IOException {
        if (value == null) {
          writer.nullValue();
        } else {
          writer.value(value.toString());
        }
      }
    };
  }

  private static class LocalDateTimeAdapter extends JsonAdapter<LocalDateTime> {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Nullable
    @Override
    public LocalDateTime fromJson(JsonReader reader) throws IOException {
      return LocalDateTime.parse(reader.nextString(), FORMATTER);
    }

    @Override
    public void toJson(JsonWriter writer, @Nullable LocalDateTime dateTime) throws IOException {
      if (dateTime == null) {
        writer.nullValue();
      } else {
        writer.value(dateTime.format(FORMATTER));
      }
    }
  }

  private static class InstantAdapter extends JsonAdapter<Instant> {
    // Jira dates are formatted as '2016-10-07T08:52:52.410-0500'. Afaict, this does not correspond
    // to an existing DateTimeFormatter predefined format (it's "almost" an OffsetDateTime, but
    // with 'faction of seconds').
    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'kk:mm:ss.SSSZ");

    @Nullable
    @Override
    public Instant fromJson(JsonReader reader) throws IOException {
      return ZonedDateTime.parse(reader.nextString(), FORMATTER).toInstant();
    }

    @Override
    public void toJson(JsonWriter writer, @Nullable Instant instant) throws IOException {
      if (instant == null) {
        writer.nullValue();
      } else {

        writer.value(ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).format(FORMATTER));
      }
    }
  }

  /**
   * A fair number of predefined "object" (resolution, status, ...) from JIRA are JSon object where
   * we only care about one of the key, which is a string, so this make this easy.
   */
  private static <T> JsonAdapter<T> objectWithOneFieldAdapter(
      String fieldName, Function<String, T> parser) {
    return new JsonAdapter<>() {
      @Nullable
      @Override
      public T fromJson(JsonReader reader) throws IOException {
        if (reader.peek() == Token.NULL) return reader.nextNull();

        String name = null;
        reader.beginObject();
        while (reader.hasNext()) {
          String key = reader.nextName();
          if (key.equals(fieldName)) {
            name = reader.nextString();
          } else {
            // Ignore other fields
            reader.skipValue();
          }
        }
        reader.endObject();
        return name == null ? null : parser.apply(name);
      }

      @Override
      public void toJson(JsonWriter writer, @Nullable T value) throws IOException {
        if (value == null) {
          writer.nullValue();
        } else {
          writer.beginObject();
          writer.name(fieldName).value(value.toString());
          writer.endObject();
        }
      }
    };
  }

  private static class CreatedIssueResponse {
    private final String key;

    private CreatedIssueResponse(String key) {
      this.key = key;
    }
  }
}
