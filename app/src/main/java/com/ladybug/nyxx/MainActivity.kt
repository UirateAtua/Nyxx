package com.ladybug.nyxx

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.database.*
import java.util.*
import android.widget.Button


data class Chat(val id: String = "", val lastMessage: String = "", val recipient: String = "")

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private val chatList = mutableListOf<Chat>()
    private var loginName: String? = null

    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var buttonLogin: Button
    private lateinit var buttonSignUp: Button
    private lateinit var dbRoot: DatabaseReference
    
    companion object {
        private var isPersistenceEnabled = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (!isPersistenceEnabled) {
            try {
                FirebaseDatabase.getInstance("https://nyxx-a2e29-default-rtdb.europe-west1.firebasedatabase.app").setPersistenceEnabled(true)
                isPersistenceEnabled = true
            } catch (e: DatabaseException) {
                // Persistence already enabled
            }
        }

        dbRoot = FirebaseDatabase.getInstance("https://nyxx-a2e29-default-rtdb.europe-west1.firebasedatabase.app").reference

        val prefs = getSharedPreferences("chat_prefs", MODE_PRIVATE)
        val savedName = prefs.getString("username", null)

        if (savedName != null) {
            loginName = savedName
            setupChatListView()
            Toast.makeText(this, "Welcome Back, $loginName", Toast.LENGTH_SHORT).show()
            listenForChats()
        } else {
            setContentView(R.layout.login)
            login()
        }
    }

    private fun setupChatListView() {
        setContentView(R.layout.activity_main)
        
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, 0, 0
        )
        toggle.drawerArrowDrawable.color = Color.WHITE
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navView.setNavigationItemSelectedListener(this)

        val headerView = navView.getHeaderView(0)
        val profileNameTextView = headerView.findViewById<TextView>(R.id.textViewProfileName)
        profileNameTextView.text = loginName

        recyclerView = findViewById(R.id.recyclerViewChats)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ChatAdapter(chatList) { chat ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("USER_NAME", loginName)
            intent.putExtra("RECIPIENT_NAME", chat.recipient)
            startActivity(intent)
        }
        recyclerView.adapter = adapter
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_new_chat -> showNewChatDialog()
            R.id.nav_settings -> Toast.makeText(this, "Settings clicked", Toast.LENGTH_SHORT).show()
            R.id.nav_about -> Toast.makeText(this, "About clicked", Toast.LENGTH_SHORT).show()
            R.id.nav_logout -> logout()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun login() {
        username = findViewById(R.id.editTextText)
        password = findViewById(R.id.editTextTextPassword)
        buttonLogin = findViewById(R.id.button1)
        buttonSignUp = findViewById(R.id.button4)

        buttonLogin.setOnClickListener {
            val name = username.text.toString().trim()
            val pass = password.text.toString()
            if (name.isEmpty()) {
                username.error = "Name required"
                return@setOnClickListener
            }
            if (pass.isEmpty()) {
                password.error = "Password required"
                return@setOnClickListener
            }

            val userRef = dbRoot.child("users").child(name)
            userRef.get().addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    Toast.makeText(this, "You have to sign up first!", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val savedPassword = snapshot.child("password").value.toString()
                if (savedPassword == pass) {
                    Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                    loginName = name
                    val prefs = getSharedPreferences("chat_prefs", MODE_PRIVATE).edit()
                    prefs.putString("username", loginName)
                    prefs.apply()
                    setupChatListView()
                    listenForChats()
                } else {
                    Toast.makeText(this, "Wrong password!!!", Toast.LENGTH_SHORT).show()
                }

            }.addOnFailureListener {
                Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show()
            }
        }

        buttonSignUp.setOnClickListener {
            val name = username.text.toString().trim()
            val pass = password.text.toString()
            if (name.isEmpty()) {
                username.error = "Name required"
                return@setOnClickListener
            }
            if (pass.isEmpty()) {
                password.error = "Password required"
                return@setOnClickListener
            }

            val userRef = dbRoot.child("users").child(name)
            userRef.get().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val snapshot = task.result
                    if (snapshot.exists()) {
                        Toast.makeText(this, "User already exists", Toast.LENGTH_SHORT).show()
                    } else {
                        val userData = mapOf(
                            "password" to pass,
                            "createdAt" to System.currentTimeMillis()
                        )
                        userRef.setValue(userData)
                            .addOnSuccessListener {
                                Toast.makeText(this, "User created! Signing in...", Toast.LENGTH_SHORT).show()
                                loginName = name
                                val prefs = getSharedPreferences("chat_prefs", MODE_PRIVATE).edit()
                                prefs.putString("username", loginName)
                                prefs.apply()
                                setupChatListView()
                                listenForChats()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Error creating user: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    Toast.makeText(this, "Signup failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun logout() {
        getSharedPreferences("chat_prefs", MODE_PRIVATE).edit().remove("username").apply()
        loginName = null
        setContentView(R.layout.login)
        login()
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
        chatList.clear()
        adapter.notifyDataSetChanged()
        val chatRoomsRef = dbRoot.child("chat_rooms")
        chatRoomsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val chatRoomId = snapshot.key ?: return
                if (loginName != null && chatRoomId.split("_").contains(loginName!!.lowercase(Locale.ROOT))) {
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
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chats[position]
        holder.recipientName.text = chat.recipient
        holder.itemView.setOnClickListener { onClick(chat) }
    }

    override fun getItemCount(): Int = chats.size

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val recipientName: TextView = itemView.findViewById(R.id.textViewRecipientName)
    }
}