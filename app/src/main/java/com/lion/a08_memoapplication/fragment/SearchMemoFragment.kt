package com.lion.a08_memoapplication.fragment

import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.lion.a08_memoapplication.MainActivity
import com.lion.a08_memoapplication.R
import com.lion.a08_memoapplication.databinding.FragmentSearchMemoBinding
import com.lion.a08_memoapplication.databinding.RowMemoBinding
import com.lion.a08_memoapplication.model.MemoModel
import com.lion.a08_memoapplication.repository.MemoRepository
import com.lion.a08_memoapplication.util.FragmentName
import com.lion.a08_memoapplication.util.MemoListName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class SearchMemoFragment : Fragment() {
    lateinit var fragmentSearchMemoBinding: FragmentSearchMemoBinding
    lateinit var mainActivity: MainActivity

    // 리사이클러뷰 구성을 위한 리스트
    var memoList = mutableListOf<MemoModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        fragmentSearchMemoBinding = FragmentSearchMemoBinding.inflate(inflater)
        mainActivity = activity as MainActivity

        // 툴바 구성
        settingToolbar()
        // RecyclerView 초기 구성
        settingRecyclerView()

        // 초기 화면에서 RecyclerView와 "검색 결과 없음" 메시지 숨기기
        fragmentSearchMemoBinding.recyclerViewSearchMemo.visibility = View.GONE
        fragmentSearchMemoBinding.textViewSearchMemo.visibility = View.GONE

        return fragmentSearchMemoBinding.root
    }

    // 툴바를 구성하는 메서드
    fun settingToolbar() {
        fragmentSearchMemoBinding.apply {
            // 뒤로가기 버튼
            toolbarSearchMemo.setNavigationOnClickListener {
                mainActivity.removeFragment(FragmentName.SEARCH_MEMO_FRAGMENT)
            }

            // 텍스트 변경 시 검색 결과 업데이트
            textFieldSearchMemo.editText?.addTextChangedListener { editable ->
                val query = editable.toString().trim()
                if (query.isNotEmpty()) {
                    // 검색어가 있을 경우 검색 실행
                    searchMemo(query)
                } else {
                    // 검색어가 없으면 RecyclerView와 메시지 숨기기
                    fragmentSearchMemoBinding.recyclerViewSearchMemo.visibility = View.GONE
                    fragmentSearchMemoBinding.textViewSearchMemo.visibility = View.GONE
                }
            }

            // 검색 버튼 클릭 시 검색 실행
            imageButtonSearchMemo.setOnClickListener {
                val query = textFieldSearchMemo.editText?.text.toString().trim()
                if (query.isNotEmpty()) {
                    searchMemo(query)
                } else {
                    // 검색어가 없으면 RecyclerView와 메시지 숨기기
                    fragmentSearchMemoBinding.recyclerViewSearchMemo.visibility = View.GONE
                    fragmentSearchMemoBinding.textViewSearchMemo.visibility = View.GONE
                }
            }
        }
    }

    // RecyclerView를 구성하는 메서드
    fun settingRecyclerView() {
        fragmentSearchMemoBinding.apply {
            recyclerViewSearchMemo.adapter = RecyclerViewSearchMemoAdapter()
            recyclerViewSearchMemo.layoutManager = LinearLayoutManager(mainActivity)
            val deco = MaterialDividerItemDecoration(mainActivity, MaterialDividerItemDecoration.VERTICAL)
            recyclerViewSearchMemo.addItemDecoration(deco)
        }
    }

    // 검색 메모 데이터를 가져오는 메서드
    fun searchMemo(query: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val work1 = async(Dispatchers.IO) {
                // 필터 조건을 함께 전달
                MemoRepository.searchMemoByTitleOrText(mainActivity, query,
                    isFavorite = when {
                        // 즐겨찾기
                        arguments?.getString("MemoName") == MemoListName.MEMO_NAME_FAVORITE.str -> true
                        else -> null
                    },
                    categoryIdx = when {
                        // 카테고리
                        arguments?.getString("MemoName") == MemoListName.MEMO_NAME_ADDED.str -> {
                            arguments?.getInt("categoryIdx") ?: -1
                        }
                        else -> null
                    }
                )
            }

            memoList = work1.await()

            // 결과가 없으면 "검색 결과가 없습니다." 메시지 표시
            if (memoList.isEmpty()) {
                fragmentSearchMemoBinding.recyclerViewSearchMemo.visibility = View.GONE
                fragmentSearchMemoBinding.textViewSearchMemo.visibility = View.VISIBLE
            } else {
                fragmentSearchMemoBinding.recyclerViewSearchMemo.visibility = View.VISIBLE
                fragmentSearchMemoBinding.textViewSearchMemo.visibility = View.GONE
            }

            fragmentSearchMemoBinding.recyclerViewSearchMemo.adapter?.notifyDataSetChanged()
        }
    }

    // RecyclerView의 어뎁터
    inner class RecyclerViewSearchMemoAdapter : RecyclerView.Adapter<RecyclerViewSearchMemoAdapter.ViewHolderSearchMemo>() {
        // ViewHolder
        inner class ViewHolderSearchMemo(val rowMemoBinding: RowMemoBinding) : RecyclerView.ViewHolder(rowMemoBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderSearchMemo {
            val rowMemoBinding = RowMemoBinding.inflate(layoutInflater, parent, false)
            val viewHolderMemoAdapter = ViewHolderSearchMemo(rowMemoBinding)

            rowMemoBinding.root.setOnClickListener {
                // 항목을 눌러 메모 보는 화면으로 이동하는 처리
                showMemoData(viewHolderMemoAdapter.adapterPosition)
            }

            // 즐겨찾기 버튼 처리
            rowMemoBinding.buttonRowFavorite.setOnClickListener {
                // 사용자가 선택한 항목 번째 객체를 가져온다.
                val memoModel = memoList[viewHolderMemoAdapter.adapterPosition]
                // 즐겨찾기 값을 반대값으로 넣어준다.
                memoModel.memoIsFavorite = !memoModel.memoIsFavorite
                // 즐겨찾기 값을 수정한다.
                CoroutineScope(Dispatchers.Main).launch {
                    val work1 = async(Dispatchers.IO) {
                        MemoRepository.updateMemoFavorite(mainActivity, memoModel.memoIdx, memoModel.memoIsFavorite)
                    }
                    work1.join()

                    // 즐겨찾기 화면에서라면 현재 메모 삭제
                    if (arguments?.getString("MemoName") == MemoListName.MEMO_NAME_FAVORITE.str) {
                        memoList.removeAt(viewHolderMemoAdapter.adapterPosition)
                        fragmentSearchMemoBinding.recyclerViewSearchMemo.adapter?.notifyItemRemoved(viewHolderMemoAdapter.adapterPosition)
                    } else {
                        val a1 = rowMemoBinding.buttonRowFavorite as MaterialButton
                        if (memoModel.memoIsFavorite) {
                            a1.setIconResource(R.drawable.star_full_24px)
                        } else {
                            a1.setIconResource(R.drawable.star_24px)
                        }
                    }
                }
            }

            return viewHolderMemoAdapter
        }

        override fun getItemCount(): Int {
            return memoList.size
        }

        override fun onBindViewHolder(holder: ViewHolderSearchMemo, position: Int) {
            if (memoList[position].memoIsSecret) {
                holder.rowMemoBinding.textViewRowTitle.text = "비밀 메모 입니다"
                holder.rowMemoBinding.textViewRowTitle.setTextColor(Color.LTGRAY)
            } else {
                holder.rowMemoBinding.textViewRowTitle.text = memoList[position].memoTitle
                holder.rowMemoBinding.textViewRowTitle.setTextColor(Color.BLACK)
            }

            val a1 = holder.rowMemoBinding.buttonRowFavorite as MaterialButton
            if (memoList[position].memoIsFavorite) {
                a1.setIconResource(R.drawable.star_full_24px)
            } else {
                a1.setIconResource(R.drawable.star_24px)
            }
        }
    }

    // 항목을 눌러 메모 보는 화면으로 이동하는 처리
    fun showMemoData(position: Int) {
        // 메모 번호를 전달한다.
        val dataBundle = Bundle()
        dataBundle.putInt("memoIdx", memoList[position].memoIdx)
        mainActivity.replaceFragment(FragmentName.READ_MEMO_FRAGMENT, true, true, dataBundle)
    }
}