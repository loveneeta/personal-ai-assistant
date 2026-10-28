package com.personalai.assistant

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.personalai.assistant.data.repository.AppRepository
import com.personalai.assistant.transcription.TranscriptionQueueManager
import com.personalai.assistant.transcription.models.ModelManager
import com.personalai.assistant.ui.chat.ChatScreen
import com.personalai.assistant.ui.chat.ChatViewModel
import com.personalai.assistant.ui.home.HomeScreen
import com.personalai.assistant.ui.home.HomeViewModel
import com.personalai.assistant.ui.meetings.MeetingDetailsScreen
import com.personalai.assistant.ui.meetings.MeetingDetailsViewModel
import com.personalai.assistant.ui.meetings.MeetingsScreen
import com.personalai.assistant.ui.meetings.MeetingsViewModel
import com.personalai.assistant.ui.memory.MemoryScreen
import com.personalai.assistant.ui.memory.MemoryViewModel
import com.personalai.assistant.ui.models.ModelsScreen
import com.personalai.assistant.ui.models.ModelsViewModel
import com.personalai.assistant.ui.recordings.RecordingDetailsScreen
import com.personalai.assistant.ui.recordings.RecordingDetailsViewModel
import com.personalai.assistant.ui.recordings.RecordingsScreen
import com.personalai.assistant.ui.recordings.RecordingsViewModel
import com.personalai.assistant.ui.settings.SettingsScreen
import com.personalai.assistant.ui.settings.SettingsViewModel
import com.personalai.assistant.ui.tasks.TasksScreen
import com.personalai.assistant.ui.tasks.TasksViewModel

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val repository = AppRepository.getInstance(context)
    val modelManager = ModelManager.getInstance(context)
    val queueManager = TranscriptionQueueManager.getInstance(context)
    val backStack = rememberNavBackStack(HomeNavKey)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<HomeNavKey> {
                val homeViewModel: HomeViewModel = viewModel {
                    HomeViewModel(repository, modelManager)
                }
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToRecordings = { backStack.add(RecordingsNavKey) },
                    onNavigateToRecordingDetails = { id -> backStack.add(RecordingDetailsNavKey(id)) },
                    onNavigateToSettings = { backStack.add(SettingsNavKey) },
                    onNavigateToMeetings = { backStack.add(MeetingsNavKey) },
                    onNavigateToTasks = { backStack.add(TasksNavKey) },
                    onNavigateToMemory = { backStack.add(MemoryNavKey) },
                    onNavigateToChat = { backStack.add(ChatNavKey) }
                )
            }

            entry<RecordingsNavKey> {
                val recordingsViewModel: RecordingsViewModel = viewModel {
                    RecordingsViewModel(repository)
                }
                RecordingsScreen(
                    viewModel = recordingsViewModel,
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onNavigateToDetails = { id -> backStack.add(RecordingDetailsNavKey(id)) }
                )
            }

            entry<RecordingDetailsNavKey> { navKey ->
                val detailsViewModel: RecordingDetailsViewModel = viewModel(key = "details_${navKey.recordingId}") {
                    RecordingDetailsViewModel(repository, queueManager, navKey.recordingId)
                }
                RecordingDetailsScreen(
                    viewModel = detailsViewModel,
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onNavigateToModels = { backStack.add(ModelsNavKey) },
                    onNavigateToMeeting = { meetingId -> backStack.add(MeetingDetailsNavKey(meetingId)) }
                )
            }

            entry<MeetingsNavKey> {
                val meetingsViewModel: MeetingsViewModel = viewModel {
                    MeetingsViewModel(repository)
                }
                MeetingsScreen(
                    viewModel = meetingsViewModel,
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onNavigateToMeetingDetails = { meetingId -> backStack.add(MeetingDetailsNavKey(meetingId)) }
                )
            }

            entry<MeetingDetailsNavKey> { navKey ->
                val meetingDetailsViewModel: MeetingDetailsViewModel = viewModel(key = "meeting_${navKey.meetingId}") {
                    MeetingDetailsViewModel(repository, navKey.meetingId)
                }
                MeetingDetailsScreen(
                    viewModel = meetingDetailsViewModel,
                    onNavigateBack = { backStack.removeLastOrNull() }
                )
            }

            entry<TasksNavKey> {
                val tasksViewModel: TasksViewModel = viewModel {
                    TasksViewModel(repository)
                }
                TasksScreen(
                    viewModel = tasksViewModel,
                    onNavigateBack = { backStack.removeLastOrNull() }
                )
            }

            entry<MemoryNavKey> {
                val memoryViewModel: MemoryViewModel = viewModel {
                    MemoryViewModel(repository)
                }
                MemoryScreen(
                    viewModel = memoryViewModel,
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onNavigateToRecording = { recId -> backStack.add(RecordingDetailsNavKey(recId)) },
                    onNavigateToMeeting = { meetId -> backStack.add(MeetingDetailsNavKey(meetId)) }
                )
            }

            entry<ChatNavKey> {
                val chatViewModel: ChatViewModel = viewModel {
                    ChatViewModel(repository)
                }
                ChatScreen(
                    viewModel = chatViewModel,
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onNavigateToRecording = { recId -> backStack.add(RecordingDetailsNavKey(recId)) },
                    onNavigateToMeeting = { meetId -> backStack.add(MeetingDetailsNavKey(meetId)) }
                )
            }

            entry<SettingsNavKey> {
                val settingsViewModel: SettingsViewModel = viewModel {
                    SettingsViewModel(repository, context)
                }
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onNavigateToModels = { backStack.add(ModelsNavKey) }
                )
            }

            entry<ModelsNavKey> {
                val modelsViewModel: ModelsViewModel = viewModel {
                    ModelsViewModel(modelManager)
                }
                ModelsScreen(
                    viewModel = modelsViewModel,
                    onNavigateBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
