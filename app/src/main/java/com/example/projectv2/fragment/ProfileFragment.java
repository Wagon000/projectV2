package com.example.projectv2.fragment;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.projectv2.LoginActivity;
import com.example.projectv2.R;
import com.example.projectv2.api.ApiClient;
import com.example.projectv2.model.User;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.BufferedSink;

public class ProfileFragment extends Fragment {
    private CardView avatarContainer;
    private ImageView avatarImage;
    private TextView usernameText;
    private TextView mbtiTypeText;
    private TextView bioText;
    private TextView gradeText;
    private TextView genderText;
    private TextView ageText;
    private View gradeContainer;
    private View genderContainer;
    private View ageContainer;
    private TextView changePasswordButton;
    private TextView logoutButton;

    private Long userId;
    private User currentUser;

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int PERMISSION_REQUEST = 2;

    public static ProfileFragment newInstance() {
        return new ProfileFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupListeners();
        loadUserInfo();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            uploadImage(data.getData());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openImagePicker();
            } else {
                Toast.makeText(requireContext(), "需要存储权限才能选择图片", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initViews(View view) {
        avatarContainer = view.findViewById(R.id.avatarContainer);
        avatarImage = view.findViewById(R.id.avatarImage);
        usernameText = view.findViewById(R.id.usernameText);
        mbtiTypeText = view.findViewById(R.id.mbtiTypeText);
        bioText = view.findViewById(R.id.bioText);
        gradeText = view.findViewById(R.id.gradeText);
        genderText = view.findViewById(R.id.genderText);
        ageText = view.findViewById(R.id.ageText);
        gradeContainer = view.findViewById(R.id.gradeContainer);
        genderContainer = view.findViewById(R.id.genderContainer);
        ageContainer = view.findViewById(R.id.ageContainer);
        changePasswordButton = view.findViewById(R.id.changePasswordButton);
        logoutButton = view.findViewById(R.id.logoutButton);

        // 从SharedPreferences获取用户ID
        SharedPreferences prefs = requireActivity().getSharedPreferences("user_info", Context.MODE_PRIVATE);
        userId = prefs.getLong("user_id", -1);
    }

    private void setupListeners() {
        avatarContainer.setOnClickListener(v -> showChangeAvatarDialog());
        usernameText.setOnClickListener(v -> showEditUsernameDialog());
        bioText.setOnClickListener(v -> showEditBioDialog());
        gradeContainer.setOnClickListener(v -> showEditGradeDialog());
        genderContainer.setOnClickListener(v -> showEditGenderDialog());
        ageContainer.setOnClickListener(v -> showEditAgeDialog());
        changePasswordButton.setOnClickListener(v -> showChangePasswordDialog());
        logoutButton.setOnClickListener(v -> showLogoutConfirmDialog());
    }

    private void loadUserInfo() {
        if (userId == -1) {
            Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        ApiClient.getUserApi().getUserInfo(userId).enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                if (response.isSuccessful() && response.body() != null) {
                    currentUser = response.body();
                    updateUI();
                }
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                Toast.makeText(getContext(), "获取用户信息失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUI() {
        if (currentUser == null) return;

        usernameText.setText(currentUser.getUsername());
        mbtiTypeText.setText(currentUser.getMbtiType() != null ? currentUser.getMbtiType() : "未完成MBTI测试");
        bioText.setText(currentUser.getBio() != null ? currentUser.getBio() : "点击添加个性签名");
        gradeText.setText(currentUser.getGrade() != null ? currentUser.getGrade() : "未设置");
        genderText.setText(currentUser.getGender() != null ? currentUser.getGender() : "未设置");
        ageText.setText(currentUser.getAge() != null ? String.valueOf(currentUser.getAge()) : "未设置");

        // 加载头像
        if (currentUser.getAvatarUrl() != null) {
            Glide.with(this)
                    .load(ApiClient.BASE_URL.substring(0, ApiClient.BASE_URL.length() - 1) + currentUser.getAvatarUrl())
                    .placeholder(R.drawable.default_avatar)
                    .error(R.drawable.default_avatar)
                    .into(avatarImage);
        }
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10及以上版本不需要存储权限就可以访问媒体文件
            openImagePicker();
        } else {
            // Android 9及以下版本需要请求存储权限
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(),
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST);
            } else {
                openImagePicker();
            }
        }
    }

    private void showChangeAvatarDialog() {
        String[] options = {"从相册选择", "取消"};
        new AlertDialog.Builder(requireContext())
                .setTitle("更换头像")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        checkStoragePermission();
                    }
                })
                .show();
    }

    private void openImagePicker() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "选择图片"), PICK_IMAGE_REQUEST);
    }

    private void uploadImage(Uri imageUri) {
        try {
            final String mimeType = requireContext().getContentResolver().getType(imageUri);
            final String finalMimeType = mimeType != null ? mimeType : "image/*";

            // 创建请求体
            RequestBody requestFile = new RequestBody() {
                @Override
                public MediaType contentType() {
                    return MediaType.parse(finalMimeType);
                }

                @Override
                public void writeTo(okio.BufferedSink sink) throws IOException {
                    InputStream inputStream = null;
                    try {
                        inputStream = requireContext().getContentResolver().openInputStream(imageUri);
                        if (inputStream == null) {
                            throw new IOException("无法打开文件流");
                        }
                        byte[] buffer = new byte[4096];
                        int read;
                        while ((read = inputStream.read(buffer)) != -1) {
                            sink.write(buffer, 0, read);
                        }
                    } finally {
                        if (inputStream != null) {
                            inputStream.close();
                        }
                    }
                }
            };

            // 创建 MultipartBody.Part
            MultipartBody.Part body = MultipartBody.Part.createFormData("file", "image.jpg", requestFile);
            RequestBody userId = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(this.userId));

            // 发送请求
            ApiClient.getUserApi().uploadAvatar(body, userId).enqueue(new Callback<Map<String, String>>() {
                @Override
                public void onResponse(Call<Map<String, String>> call, Response<Map<String, String>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        String avatarUrl = response.body().get("url");
                        // 更新UI显示新头像
                        if (isAdded() && getContext() != null) {
                            Glide.with(ProfileFragment.this)
                                    .load(ApiClient.BASE_URL.substring(0, ApiClient.BASE_URL.length() - 1) + avatarUrl)
                                    .placeholder(R.drawable.default_avatar)
                                    .error(R.drawable.default_avatar)
                                    .into(avatarImage);
                            Toast.makeText(requireContext(), "头像上传成功", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        if (isAdded() && getContext() != null) {
                            Toast.makeText(requireContext(), "头像上传失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                }

                @Override
                public void onFailure(Call<Map<String, String>> call, Throwable t) {
                    if (isAdded() && getContext() != null) {
                        Toast.makeText(requireContext(), "头像上传失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });

        } catch (Exception e) {
            if (isAdded() && getContext() != null) {
                Toast.makeText(requireContext(), "文件处理失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showEditUsernameDialog() {
        EditText input = new EditText(requireContext());
        input.setText(currentUser.getUsername());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);

        new AlertDialog.Builder(requireContext())
                .setTitle("修改用户名")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String newUsername = input.getText().toString().trim();
                    if (newUsername.isEmpty()) {
                        Toast.makeText(requireContext(), "用户名不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newUsername.equals(currentUser.getUsername())) {
                        return;
                    }
                    updateUserField("username", newUsername);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showEditBioDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("编辑个性签名");

        final EditText input = new EditText(requireContext());
        input.setText(currentUser.getBio());
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String newBio = input.getText().toString().trim();
            updateUserField("bio", newBio);
        });
        builder.setNegativeButton("取消", null);

        builder.show();
    }

    private void showEditGradeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("选择年级");

        final String[] grades = {"大一", "大二", "大三", "大四", "研究生"};
        builder.setItems(grades, (dialog, which) -> {
            String selectedGrade = grades[which];
            updateUserField("grade", selectedGrade);
        });

        builder.show();
    }

    private void showEditGenderDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("选择性别");

        final String[] genders = {"男", "女", "其他"};
        builder.setItems(genders, (dialog, which) -> {
            String selectedGender = genders[which];
            updateUserField("gender", selectedGender);
        });

        builder.show();
    }

    private void showEditAgeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("编辑年龄");

        final EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        if (currentUser.getAge() != null) {
            input.setText(String.valueOf(currentUser.getAge()));
        }
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            try {
                int newAge = Integer.parseInt(input.getText().toString().trim());
                if (newAge > 0 && newAge < 150) {
                    updateUserField("age", String.valueOf(newAge));
                } else {
                    Toast.makeText(getContext(), "请输入有效年龄", Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "请输入有效数字", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", null);

        builder.show();
    }

    private void showChangePasswordDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_change_password, null);
        EditText oldPasswordInput = dialogView.findViewById(R.id.oldPasswordInput);
        EditText newPasswordInput = dialogView.findViewById(R.id.newPasswordInput);
        EditText confirmPasswordInput = dialogView.findViewById(R.id.confirmPasswordInput);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("修改密码")
                .setView(dialogView)
                .setPositiveButton("确定", (dialog, which) -> {
                    String oldPassword = oldPasswordInput.getText().toString().trim();
                    String newPassword = newPasswordInput.getText().toString().trim();
                    String confirmPassword = confirmPasswordInput.getText().toString().trim();

                    if (oldPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                        Toast.makeText(getContext(), "请填写所有字段", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!newPassword.equals(confirmPassword)) {
                        Toast.makeText(getContext(), "新密码两次输入不一致", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    updatePassword(oldPassword, newPassword);
                })
                .setNegativeButton("取消", null);

        builder.show();
    }

    private void showLogoutConfirmDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("退出登录")
                .setMessage("确定要退出登录吗？")
                .setPositiveButton("确定", (dialog, which) -> logout())
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateUserField(String field, String value) {
        Map<String, String> updateData = new HashMap<>();
        updateData.put(field, value);

        ApiClient.getUserApi().updateUserField(userId, updateData).enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                if (response.isSuccessful() && response.body() != null) {
                    currentUser = response.body();
                    updateUI();
                    Toast.makeText(getContext(), "更新成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "更新失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                Toast.makeText(getContext(), "更新失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updatePassword(String oldPassword, String newPassword) {
        Map<String, String> passwordData = new HashMap<>();
        passwordData.put("oldPassword", oldPassword);
        passwordData.put("newPassword", newPassword);

        ApiClient.getUserApi().updatePassword(userId, passwordData).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(getContext(), "密码修改成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "密码修改失败，请检查原密码是否正确", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(getContext(), "密码修改失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void logout() {
        // 清除SharedPreferences中的用户信息
        SharedPreferences.Editor editor = requireActivity().getSharedPreferences("user_info", Context.MODE_PRIVATE).edit();
        editor.clear();
        editor.apply();

        // 跳转到登录页面
        Intent intent = new Intent(requireActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
} 