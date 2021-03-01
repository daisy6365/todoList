package com.example.todolist

import android.app.Activity
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.TextView
import androidx.activity.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.todolist.databinding.ActivityMainBinding
import com.example.todolist.databinding.ItemTodoBinding
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {
    val RC_SIGN_IN = 1000;

    //xml 뒤에 binding이 붙어서 생겨있음
    // 객체를 통해 조작을 자유롭게 함
    private lateinit var binding: ActivityMainBinding

    //viewModel을 사용하면 화면회전을 해도 데이터가 잘 유지됨
    // liveData 적용 -> 코드 간결, 화면 갱신하는 코드를 한쪽에 몰아넣을 수 있음
    // databinding과 함께하면 더 간결해짐
    private val viewModel : MainViewModel by viewModels()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)


        //실행하자마자 로그인뷰 실행
        // 로그인이 안됐을때 -> 로그인창 뜸
        if(FirebaseAuth.getInstance().currentUser == null) {
            login()
        }


        //recyclerview에 layoutmanager를 넣음 //어댑터를 어댑터프로퍼티에 지정
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = TodoAdapter(emptyList(),
                    onClickDeleteIcon = {
//                        deleteTodo(it)
                        viewModel.deleteTodo(it)
                    },
                    onClickItem = {
//                        toggleTodo(it)
                        viewModel.toggleTodo(it)
                    }
            )
        }

        binding.addButton.setOnClickListener {
            val todo = Todo(binding.editText.text.toString())
            viewModel.addTodo(todo)
        }

        //관찰 UI 업데이트
        viewModel.todoLiveData.observe(this, Observer {
            //최신데이터가 넘어옴 -> 어댑터의 값을 갱신해줌
            (binding.recyclerView.adapter as TodoAdapter).setData(it)
        })
    }

    // 로그인처리에 대한 결과
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val response = IdpResponse.fromResultIntent(data)

            //로그인 성공
            if (resultCode == Activity.RESULT_OK) {
                // 가입 화
                // Successfully signed in
                viewModel.fetchData()
            }
            // 로그인 실패
            else {
                // Sign in failed. If response is null the user canceled the
                // sign-in flow using the back button. Otherwise check
                // response.getError().getErrorCode() and handle the error.
                // ...
                finish()
            }
        }
    }
    fun login() {
        val providers = arrayListOf(AuthUI.IdpConfig.EmailBuilder().build())

        // Create and launch sign-in intent
        startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .build(),
            RC_SIGN_IN)
    }

    //로그아웃
    fun logout() {
        AuthUI.getInstance()
            .signOut(this)
            .addOnCompleteListener {
                // 로그아웃 성공시 코드 실행
                login()
            }
    }

    //로그아웃 하는 item 추가
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.main, menu)
        return true
    }
    //로그아웃 이벤트 처리
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.action_log_out -> {
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

}

//할일 객체
data class Todo(val text : String, var isDone : Boolean = false)

class TodoAdapter(private var myDataset: List<DocumentSnapshot>,
                  val onClickDeleteIcon:(todo :DocumentSnapshot) -> Unit,
                  val onClickItem : (todo : DocumentSnapshot) -> Unit
                  ) :
    RecyclerView.Adapter<TodoAdapter.TodoViewHolder>() {


    //view 전체를 받게끔
    class TodoViewHolder(val binding: ItemTodoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoAdapter.TodoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_todo, parent, false)

        // 모든 아이들을 binding객체로 연결이 됨
        return TodoViewHolder(ItemTodoBinding.bind(view))
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        val todo = myDataset[position]
        holder.binding.todoText.text = todo.getString("text") ?: ""

        //할일을 다 했다면 -> true => 밑줄
        if(todo.getBoolean("isDone")?: false == true){
            holder.binding.todoText.apply {
                //사선
                paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG

                // 이탤릭체
                setTypeface(null, Typeface.ITALIC)
            }
        }else{// 할일을 다 안끝냈다면 -> false
            holder.binding.todoText.apply {
                paintFlags = 0 // 계속 nomal 값
                setTypeface(null, Typeface.NORMAL)
            }
        }

        holder.binding.deleteImageView.setOnClickListener{
            onClickDeleteIcon.invoke(todo)
        }
        holder.binding.root.setOnClickListener {
            onClickItem.invoke(todo)
        }
    }

    override fun getItemCount() = myDataset.size

    fun setData(newData : List<DocumentSnapshot>){
        myDataset = newData
        notifyDataSetChanged()//데이터가 바뀔때마다 이 데이터 실행
    }
}

// 화면 돌렸을때 activity 재실행 되지 않도록 함
// data 관련된것을 activity가 아닌 ViewModel이 하게 함
class MainViewModel : ViewModel() {
    val db = Firebase.firestore

    //변경 가능
    val todoLiveData = MutableLiveData<List<DocumentSnapshot>>()

    // Firebase cloud(database)에 있는 데이터들 읽어오기
    // 초기화
    init {
        fetchData()
    }

    fun fetchData() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            db.collection(user.uid)
                    //database -> 코드를 서로 연동
                    //database 쪽에서만 조작시 결과값 업데이트 됨
                .addSnapshotListener { value, e ->
                    //에러시 종료
                    if (e != null) {
                        return@addSnapshotListener
                    }
                    if(value != null){
                        todoLiveData.value = value.documents
                    }
                }

        }
    }


    //기능 구현 (할일 추가)
     fun addTodo(todo : Todo){
        //널이 아닐때 실행
        FirebaseAuth.getInstance().currentUser?. let{user ->
            db.collection(user.uid).add(todo)
        }
    }

    //data 삭제기능
     fun deleteTodo(todo: DocumentSnapshot){
        FirebaseAuth.getInstance().currentUser?. let{user ->
            db.collection(user.uid).document(todo.id).delete()
        }
    }

    // 다 한 일 다시 해제할수 있게하는 기능
     fun toggleTodo(todo : DocumentSnapshot){
        FirebaseAuth.getInstance().currentUser?. let{user ->
            val isDone = todo.getBoolean("isDone") ?: false
            db.collection(user.uid).document(todo.id).update("isDone",!isDone)
        }
    }

}
//말 쳐 못알아들을때는 rebuild project -> run