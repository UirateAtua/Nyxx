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
import androidx.activity.addCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.database.*
import java.security.MessageDigest
import java.util.*

data class Chat(
    val id: String = "",
    val lastMessage: String = "",
    val recipient: String = "",
    var unreadCount: Int = 0
)

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private val chatList = mutableListOf<Chat>()
    private var loginName: String? = null

    // Login Views
    private lateinit var textInputLayoutUsername: TextInputLayout
    private lateinit var textInputLayoutPassword: TextInputLayout
    private lateinit var editTextUsername: TextInputEditText
    private lateinit var editTextPassword: TextInputEditText
    private lateinit var buttonLogin: MaterialButton
    private lateinit var buttonSignUp: MaterialButton

    private lateinit var dbRoot: DatabaseReference
    private val chatListeners = mutableListOf<ValueEventListener>()
    private var chatRoomListener: ChildEventListener? = null

    companion object {
        private const val FIREBASE_URL = "https://nyxx-a2e29-default-rtdb.europe-west1.firebasedatabase.app"
        private var isPersistenceEnabled = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isPersistenceEnabled) {
            try {
                FirebaseDatabase.getInstance(FIREBASE_URL).setPersistenceEnabled(true)
                isPersistenceEnabled = true
            } catch (e: DatabaseException) {
                // Persistence already enabled
            }
        }

        dbRoot = FirebaseDatabase.getInstance(FIREBASE_URL).reference

        val prefs = getSharedPreferences("chat_prefs", MODE_PRIVATE)
        val savedName = prefs.getString("username", null)

        if (savedName != null) {
            loginName = savedName
            setupChatListView()
            Toast.makeText(this, getString(R.string.welcome_back, loginName), Toast.LENGTH_SHORT).show()
            listenForChats()
        } else {
            setContentView(R.layout.login)
            initLoginViews()
        }

        onBackPressedDispatcher.addCallback(this) {
            if (::drawerLayout.isInitialized && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                if (isEnabled) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        chatRoomListener?.let { dbRoot.child("chat_rooms").removeEventListener(it) }
        chatListeners.forEach { dbRoot.removeEventListener(it) }
        chatListeners.clear()
    }

    private fun setupChatListView() {
        setContentView(R.layout.activity_main)
        window.statusBarColor = getColor(R.color.colorPrimaryDark)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.title_chats)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)

        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.nav_new_chat,
            R.string.nav_logout
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
        recyclerView.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )

        adapter = ChatAdapter(chatList) { chat ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("USER_NAME", loginName)
            intent.putExtra("RECIPIENT_NAME", chat.recipient)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        // FAB for New Chat
        val fabNewChat: FloatingActionButton = findViewById(R.id.fabNewChat)
        fabNewChat.setOnClickListener { showNewChatDialog() }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_new_chat -> showNewChatDialog()
            R.id.nav_settings -> Toast.makeText(this, R.string.nav_settings, Toast.LENGTH_SHORT).show()
            R.id.nav_about -> Toast.makeText(this, R.string.nav_about, Toast.LENGTH_SHORT).show()
            R.id.nav_logout -> logout()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun initLoginViews() {
        window.statusBarColor = getColor(R.color.colorPrimaryDark)
        textInputLayoutUsername = findViewById(R.id.textInputLayoutUsername)
        textInputLayoutPassword = findViewById(R.id.textInputLayoutPassword)
        editTextUsername = findViewById(R.id.editTextUsername)
        editTextPassword = findViewById(R.id.editTextPassword)
        buttonLogin = findViewById(R.id.buttonLogin)
        buttonSignUp = findViewById(R.id.buttonSignUp)

        buttonLogin.setOnClickListener { handleLogin() }
        buttonSignUp.setOnClickListener { handleSignUp() }
    }

    private fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun handleLogin() {
        val name = editTextUsername.text.toString().trim()
        val pass = editTextPassword.text.toString()

        if (name.isEmpty()) {
            textInputLayoutUsername.error = getString(R.string.error_username_required)
            return
        } else {
            textInputLayoutUsername.error = null
        }

        if (pass.isEmpty()) {
            textInputLayoutPassword.error = getString(R.string.error_password_required)
            return
        } else {
            textInputLayoutPassword.error = null
        }

        val userRef = dbRoot.child("users").child(name)
        userRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                Toast.makeText(this, R.string.error_user_not_found, Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            val savedPasswordHash = snapshot.child("password").value.toString()
            if (savedPasswordHash == hashPassword(pass)) {
                Toast.makeText(this, R.string.success_login, Toast.LENGTH_SHORT).show()
                loginName = name
                saveLoginSession(name)
                setupChatListView()
                listenForChats()
            } else {
                Toast.makeText(this, R.string.error_wrong_password, Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, R.string.error_login_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSignUp() {
        val name = editTextUsername.text.toString().trim()
        val pass = editTextPassword.text.toString()

        if (name.isEmpty()) {
            textInputLayoutUsername.error = getString(R.string.error_username_required)
            return
        } else {
            textInputLayoutUsername.error = null
        }

        if (pass.isEmpty()) {
            textInputLayoutPassword.error = getString(R.string.error_password_required)
            return
        } else {
            textInputLayoutPassword.error = null
        }

        val userRef = dbRoot.child("users").child(name)
        userRef.get().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val snapshot = task.result
                if (snapshot.exists()) {
                    Toast.makeText(this, R.string.error_user_exists, Toast.LENGTH_SHORT).show()
                } else {
                    val userData = mapOf(
                        "password" to hashPassword(pass),
                        "createdAt" to System.currentTimeMillis()
                    )
                    userRef.setValue(userData)
                        .addOnSuccessListener {
                            Toast.makeText(this, R.string.success_signup, Toast.LENGTH_SHORT).show()
                            loginName = name
                            saveLoginSession(name)
                            setupChatListView()
                            listenForChats()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                this,
                                "${getString(R.string.error_signup_failed)}: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            } else {
                Toast.makeText(
                    this,
                    "${getString(R.string.error_signup_failed)}: ${task.exception?.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun saveLoginSession(name: String) {
        val prefs = getSharedPreferences("chat_prefs", MODE_PRIVATE).edit()
        prefs.putString("username", name)
        prefs.apply()
    }

    private fun logout() {
        getSharedPreferences("chat_prefs", MODE_PRIVATE).edit().remove("username").apply()
        loginName = null
        setContentView(R.layout.login)
        initLoginViews()
    }

    private fun showNewChatDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.dialog_new_chat_title)

        val input = EditText(this)
        input.hint = getString(R.string.dialog_hint_recipient)
        builder.setView(input)

        builder.setPositiveButton(R.string.dialog_button_create) { _, _ ->
            val recipientName = input.text.toString().trim()
            if (recipientName.isNotEmpty()) {
                val intent = Intent(this, ChatActivity::class.java)
                intent.putExtra("USER_NAME", loginName)
                intent.putExtra("RECIPIENT_NAME", recipientName)
                startActivity(intent)
            } else {
                Toast.makeText(this, R.string.error_recipient_empty, Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton(R.string.dialog_button_cancel, null)
        builder.show()
    }

    private fun listenForChats() {
        chatList.clear()
        adapter.notifyDataSetChanged()
        val chatRoomsRef = dbRoot.child("chat_rooms")

        chatRoomListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val chatRoomId = snapshot.key ?: return
                val currentUser = loginName?.lowercase(Locale.ROOT) ?: return

                val parts = chatRoomId.split("_")
                if (!parts.contains(currentUser)) return

                val recipient = parts.first { it != currentUser }

                val chat = Chat(
                    id = chatRoomId,
                    lastMessage = " ",
                    recipient = recipient.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                    },
                    unreadCount = 0
                )

                chatList.add(chat)
                adapter.notifyItemInserted(chatList.size - 1)

                val unreadListener = dbRoot.child("chat_rooms")
                    .child(chatRoomId)
                    .child("unreadCount")
                    .child(currentUser)
                    .addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(unreadSnapshot: DataSnapshot) {
                            val count = unreadSnapshot.getValue(Int::class.java) ?: 0
                            val index = chatList.indexOfFirst { it.id == chatRoomId }
                            if (index != -1) {
                                chatList[index].unreadCount = count
                                adapter.notifyItemChanged(index)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {}
                    })
                chatListeners.add(unreadListener)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }

        chatRoomsRef.addChildEventListener(chatRoomListener!!)
    }
}

// Adapter Class
class ChatAdapter(
    private val chats: List<Chat>,
    private val onClick: (Chat) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chats[position]
        holder.recipientName.text = chat.recipient

        if (chat.unreadCount > 0) {
            holder.unreadBadge.text = chat.unreadCount.toString()
            holder.unreadBadge.visibility = View.VISIBLE
        } else {
            holder.unreadBadge.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onClick(chat) }
    }

    override fun getItemCount(): Int = chats.size

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val recipientName: TextView = itemView.findViewById(R.id.textViewRecipientName)
        val unreadBadge: TextView = itemView.findViewById(R.id.textViewUnreadBadge)
    }
}
