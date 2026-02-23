package com.ladybug.nyxx

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.*
import java.util.*

data class Chat(val id: String = "", val lastMessage: String = "", val recipient: String = "")

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fabNewChat: FloatingActionButton
    private lateinit var adapter: ChatAdapter
    private val chatList = mutableListOf<Chat>()
    private var loginName: String? = null
    private lateinit var dbRoot: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerViewChats)
        fabNewChat = findViewById(R.id.fabNewChat)

        dbRoot = FirebaseDatabase.getInstance().reference

        adapter = ChatAdapter(chatList) { chat ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("USER_NAME", loginName)
            intent.putExtra("RECIPIENT_NAME", chat.recipient)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        fabNewChat.setOnClickListener {
            showNewChatDialog()
        }

        showLoginDialog()
    }

    private fun showLoginDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Login")
        val input = EditText(this)
        input.hint = "Enter your name"
        builder.setView(input)
        builder.setPositiveButton("OK") { _, _ ->
            loginName = input.text.toString().trim()
            if (loginName!!.isNotEmpty()) {
                Toast.makeText(this, "Welcome, $loginName", Toast.LENGTH_SHORT).show()
                listenForChats()
            } else {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                showLoginDialog()
            }
        }
        builder.setCancelable(false)
        builder.show()
    }

    private fun showNewChatDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("New Chat")
        val input = EditText(this)
        input.hint = "Enter recipient's name"
        builder.setView(input)
        builder.setPositiveButton("Create") { _, _ ->
            val recipientName = input.text.toString().trim()
            if (recipientName.isNotEmpty()) {
                val intent = Intent(this, ChatActivity::class.java)
                intent.putExtra("USER_NAME", loginName)
                intent.putExtra("RECIPIENT_NAME", recipientName)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Recipient name cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun listenForChats() {
        val chatRoomsRef = dbRoot.child("chat_rooms")
        chatRoomsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val chatRoomId = snapshot.key ?: return
                if (chatRoomId.split("_").contains(loginName!!.lowercase(Locale.ROOT))) {
                    val recipient = chatRoomId.split("_").first { it != loginName!!.lowercase(Locale.ROOT) }
                    val chat = Chat(chatRoomId, "", recipient.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() })
                    chatList.add(chat)
                    adapter.notifyItemInserted(chatList.size - 1)
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}

class ChatAdapter(private val chats: List<Chat>, private val onClick: (Chat) -> Unit) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chats[position]
        holder.textView.text = chat.recipient
        holder.itemView.setOnClickListener { onClick(chat) }
    }

    override fun getItemCount(): Int = chats.size

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(android.R.id.text1)
    }
}