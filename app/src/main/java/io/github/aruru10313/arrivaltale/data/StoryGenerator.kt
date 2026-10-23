package io.github.aruru10313.arrivaltale.data

import android.content.Context
import io.github.aruru10313.arrivaltale.domain.AgeGroup
import io.github.aruru10313.arrivaltale.domain.Story
import io.github.aruru10313.arrivaltale.domain.StoryTheme
import io.github.aruru10313.arrivaltale.domain.StoryChapter
import org.json.JSONObject
import org.json.JSONArray

interface StoryGenerator {
    fun stories(ageGroup: AgeGroup): List<Story>
}

class LocalStoryGenerator(context: Context) : StoryGenerator {
    private val stories = context.assets.open("stories.json").bufferedReader().use { reader ->
        JSONArray(reader.readText()).let { json ->
            (0 until json.length()).map { storyFromJson(json.getJSONObject(it)) }
        }
    }

    override fun stories(ageGroup: AgeGroup): List<Story> = stories

    private fun storyFromJson(json: JSONObject) = Story(
        id = json.getString("id"),
        title = json.getString("title"),
        theme = StoryTheme.valueOf(json.getString("theme")),
        chapters = json.getJSONArray("chapters").let { chapters ->
            (0 until chapters.length()).map { index ->
                val chapter = chapters.getJSONObject(index)
                StoryChapter(
                    id = chapter.getString("id"),
                    title = chapter.getString("title"),
                    text = chapter.getString("text"),
                    estimatedSeconds = chapter.getInt("estimatedSeconds"),
                    compressedText = chapter.optString("compressedText").takeIf(String::isNotBlank),
                    compressedSeconds = chapter.optInt("compressedSeconds").takeIf { it > 0 },
                    isEnding = chapter.optBoolean("isEnding", false),
                )
            }
        },
    )
}
