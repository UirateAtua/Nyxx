package com.ladybug.nyxx

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import java.util.*

data class Message(val text: String = "", val user: String = "", val timestamp: Long = 0)

class ChatActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSend: Button
    private lateinit var rootLayout: LinearLayout

    private lateinit var dbRoot: DatabaseReference
    private var chatRoomDb: DatabaseReference? = null
    private var childEventListener: ChildEventListener? = null
    private val messageList = mutableListOf<Message>()
    private lateinit var adapter: MessageAdapter

    private var loginName: String? = null
    private var recipientName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        recyclerView = findViewById(R.id.recyclerViewMessages)
        editTextMessage = findViewById(R.id.editTextMessage)
        buttonSend = findViewById(R.id.buttonSend)
        rootLayout = findViewById(R.id.root_layout)

        loginName = intent.getStringExtra("USER_NAME")
        recipientName = intent.getStringExtra("RECIPIENT_NAME")

        title = recipientName

        setupKeyboardHandling()

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = MessageAdapter(messageList, loginName)
        recyclerView.adapter = adapter

        dbRoot = FirebaseDatabase.getInstance().reference

        if (loginName != null && recipientName != null) {
            startChatWith(recipientName!!)
        }

        buttonSend.setOnClickListener {
            val inputText = editTextMessage.text.toString().trim()
            if (inputText.isNotEmpty()) {
                sendMessage(inputText)
                editTextMessage.text.clear()
            }
        }
    }

    private fun startChatWith(recipient: String) {
        val chatRoomId = getChatRoomId(loginName!!, recipient)
        chatRoomDb = dbRoot.child("chat_rooms").child(chatRoomId)

        listenForMessages()
    }

    private fun getChatRoomId(user1: String, user2: String): String {
        return listOf(user1, user2).sorted().joinToString("_") { it.lowercase(Locale.ROOT) }
    }

    private fun sendMessage(text: String) {
        val currentUser = loginName ?: return
        val timestamp = System.currentTimeMillis()
        val message = Message(text, currentUser, timestamp)

        chatRoomDb?.push()?.setValue(message)
            ?.addOnFailureListener { e ->
                Toast.makeText(this, "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun listenForMessages() {
        if (childEventListener != null) {
            chatRoomDb?.removeEventListener(childEventListener!!)
        }

        childEventListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val message = snapshot.getValue(Message::class.java)
                if (message != null) {
                    messageList.add(message)
                    adapter.notifyItemInserted(messageList.size - 1)
                    recyclerView.scrollToPosition(messageList.size - 1)
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ChatActivity, "Failed to listen for messages.", Toast.LENGTH_SHORT).show()
            }
        }
        chatRoomDb?.addChildEventListener(childEventListener!!)
    }

    private fun setupKeyboardHandling() {
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, imeInsets.bottom)
            insets
        }
    }
}

class MessageAdapter(private val messages: List<Message>, private var currentUser: String?) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2

    fun setCurrentUser(user: String?) {
        currentUser = user
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return if (message.user == currentUser) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutId = if (viewType == VIEW_TYPE_SENT) {
            R.layout.item_message_sent
        } else {
            R.layout.item_message_received
        }
        val view = LayoutInflater.from(parent.context)
            .inflate(layoutId, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val msg = messages[position]
        holder.textView.text = msg.text
    }

    override fun getItemCount(): Int = messages.size

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(R.id.textViewMessage)
    }
}