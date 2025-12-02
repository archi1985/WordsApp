package com.example.wordsapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.wordsapp.database.WordEntity

@Composable
fun WordsScreen(
    words: List<WordEntity>,
    learningWords: List<WordEntity>,
    onMarkUnknown: (Int) -> Unit,
    onDelete: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "All Words",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(words) { word ->
                val isInLearningSet = learningWords.any { it.id == word.id }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)//,
                    //elevation = CardDefaults.cardElevation(100.dp)

                    .alpha(0.9f)
                    ,shape = RoundedCornerShape(5.dp) //
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text(
                            "${word.english} - ${word.russian}",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isInLearningSet) {
                                Button(
                                    onClick = { onMarkUnknown(word.id) },
                                    modifier = Modifier.height(34.dp),
                                    shape = RoundedCornerShape(5.dp)
                                ) {
                                    Text("Add to Study")
                                }
                            } else {
                                Text(
                                    "In working set",
                                    color = Color(0xFF388E3C)
                                )
                            }

                            // this pushes the Delete button to the right
                            Spacer(modifier = Modifier.weight(1f))

                            IconButton(
                                onClick = { onDelete(word.id) },   // maybe later change to onSpeak(word) ?
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Cancel,
                                    contentDescription = "Delete word",
                                    modifier = Modifier.size(42.dp),
                                    tint = Color.Red
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
