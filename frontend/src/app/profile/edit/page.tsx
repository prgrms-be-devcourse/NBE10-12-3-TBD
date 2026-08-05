"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, Camera, Eye, EyeOff } from "lucide-react";
import AppShell from "@/components/AppShell";
import { apiFetch, apiFetchJson, getImageUrl } from "@/lib/api";
import { getStoredUser, setStoredUser } from "@/lib/user";

interface UserProfile {
  id: number;
  nickname: string;
  profileImage: string | null;
  email: string;
  provider: string;
}

export default function EditProfilePage() {
  const router = useRouter();

  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);

  const [nickname, setNickname] = useState("");
  const [email, setEmail] = useState("");
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [previewImage, setPreviewImage] = useState("");
  const [isUploadingImage, setIsUploadingImage] = useState(false);

  useEffect(() => {
    const stored = getStoredUser();
    if (!stored) {
      router.push("/login");
      return;
    }

    const load = async () => {
      const res = await apiFetchJson<UserProfile>(`/api/v1/users/${stored.userId}`);
      if (res.ok && res.data) {
        setUser(res.data);
        setNickname(res.data.nickname);
        setEmail(res.data.email);
        setPreviewImage(getImageUrl(res.data.profileImage) ?? "/default-profile.png");
      } else {
        alert(res.message || "프로필 정보를 불러오지 못했습니다.");
      }
      setLoading(false);
    };

    load();
  }, [router]);

  const handleImageChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !user) return;

    if (file.size > 10 * 1024 * 1024) {
      alert("10MB 이하의 이미지 파일만 업로드할 수 있습니다.");
      return;
    }

    // 이전 blob 미리보기 URL을 해제하지 않으면 사진을 여러 번 바꿀 때마다 계속 쌓인다.
    if (previewImage.startsWith("blob:")) {
      URL.revokeObjectURL(previewImage);
    }

    const objectUrl = URL.createObjectURL(file);
    setPreviewImage(objectUrl);
    setIsUploadingImage(true);

    const formData = new FormData();
    formData.append("image", file);

    const res = await apiFetch("/api/v1/users/me/image", {
      method: "PATCH",
      body: formData,
    });
    const json = await res.json().catch(() => ({}));

    setIsUploadingImage(false);

    if (res.ok && json.data) {
      setStoredUser({
        userId: json.data.id,
        nickname: json.data.nickname,
        profileImage: json.data.profileImage,
        email: json.data.email,
      });
      window.dispatchEvent(new Event("login-state-change"));
      // user state도 함께 갱신해야 한다. 안 그러면 이번 변경은 성공했는데 바로 다음
      // 시도가 실패했을 때, 실패 처리의 폴백(아래 else 분기)이 방금 저장된 이미지가
      // 아니라 페이지를 처음 열었을 때의 이미지로 되돌아간다.
      setUser({ ...json.data });
      // 업로드가 끝나면 서버가 내려준 실제 URL로 교체하고, 더 이상 필요 없는 로컬
      // blob 미리보기는 바로 해제한다.
      setPreviewImage(getImageUrl(json.data.profileImage) ?? "/default-profile.png");
    } else {
      alert(json.message || "프로필 이미지 변경에 실패했습니다.");
      setPreviewImage(getImageUrl(user.profileImage) ?? "/default-profile.png");
    }
    URL.revokeObjectURL(objectUrl);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user) return;

    if (user.provider === "LOCAL" && newPassword && newPassword !== confirmPassword) {
      alert("새 비밀번호가 일치하지 않습니다.");
      return;
    }

    const body: Record<string, string | null> = { nickname };

    if (user.provider === "LOCAL") {
      body.email = email;
      if (newPassword) {
        body.currentPassword = currentPassword;
        body.newPassword = newPassword;
      }
    }

    const res = await apiFetchJson<UserProfile>("/api/v1/users/me", {
      method: "PATCH",
      body: JSON.stringify(body),
    });

    if (res.ok && res.data) {
      setStoredUser({
        userId: res.data.id,
        nickname: res.data.nickname,
        profileImage: res.data.profileImage,
        email: res.data.email,
      });
      window.dispatchEvent(new Event("login-state-change"));
      alert("프로필이 수정되었습니다.");
      router.push("/profile");
    } else {
      alert(res.message || "프로필 수정에 실패했습니다.");
    }
  };

  if (loading) {
    return (
      <AppShell>
        <div className="mx-auto max-w-xl space-y-5">
          <div className="h-8 w-40 rounded-lg bg-surface-soft animate-pulse" />
          <div className="h-96 rounded-2xl bg-surface border border-hairline-soft animate-pulse" />
        </div>
      </AppShell>
    );
  }

  if (!user) {
    return (
      <AppShell>
        <p className="py-20 text-center text-sm text-muted">프로필 정보를 불러오지 못했습니다.</p>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <div className="mx-auto max-w-xl space-y-5">
        <div className="flex items-center gap-3">
          <Link href="/profile" className="text-muted hover:text-ink transition-colors">
            <ArrowLeft className="h-5 w-5" />
          </Link>
          <h2 className="text-xl font-bold text-ink">내 정보 수정</h2>
        </div>

        <form onSubmit={handleSubmit} className="rounded-2xl bg-surface p-6 border border-hairline-soft shadow-sm space-y-6">
          {/* Profile image */}
          <div className="flex flex-col items-center">
            <div className="relative">
              <img
                src={previewImage}
                alt="프로필 미리보기"
                className="h-24 w-24 rounded-full object-cover ring-4 ring-primary/15"
              />
              <label className="absolute bottom-0 right-0 flex h-8 w-8 cursor-pointer items-center justify-center rounded-full bg-primary text-white shadow-md hover:bg-primary-active transition-colors">
                <Camera className="h-4 w-4" />
                <input
                  type="file"
                  accept=".jpg,.jpeg,.png"
                  className="hidden"
                  onChange={handleImageChange}
                />
              </label>
            </div>
            <p className="mt-3 text-xs text-muted">프로필 사진 변경</p>
          </div>

          {/* Nickname */}
          <div>
            <label className="text-xs font-bold text-muted mb-1.5 block">닉네임</label>
            <input
              type="text"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              className="w-full rounded-xl border border-hairline bg-surface-soft px-4 py-2.5 text-sm text-ink focus:border-primary focus:outline-hidden"
              required
            />
          </div>

          {/* Email login only */}
          {user.provider === "LOCAL" ? (
            <>
              <div>
                <label className="text-xs font-bold text-muted mb-1.5 block">이메일</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full rounded-xl border border-hairline bg-surface-soft px-4 py-2.5 text-sm text-ink focus:border-primary focus:outline-hidden"
                  required
                />
              </div>

              <div className="border-t border-hairline-soft pt-6 space-y-4">
                <p className="text-sm font-bold text-ink">비밀번호 변경</p>

                <div>
                  <label className="text-xs font-bold text-muted mb-1.5 block">현재 비밀번호</label>
                  <div className="relative">
                    <input
                      type={showPassword ? "text" : "password"}
                      value={currentPassword}
                      onChange={(e) => setCurrentPassword(e.target.value)}
                      placeholder="현재 비밀번호 입력"
                      className="w-full rounded-xl border border-hairline bg-surface-soft px-4 py-2.5 pr-10 text-sm text-ink focus:border-primary focus:outline-hidden"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-muted hover:text-ink"
                    >
                      {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                </div>

                <div>
                  <label className="text-xs font-bold text-muted mb-1.5 block">새 비밀번호</label>
                  <input
                    type="password"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    placeholder="새 비밀번호"
                    className="w-full rounded-xl border border-hairline bg-surface-soft px-4 py-2.5 text-sm text-ink focus:border-primary focus:outline-hidden"
                  />
                </div>

                <div>
                  <label className="text-xs font-bold text-muted mb-1.5 block">새 비밀번호 확인</label>
                  <input
                    type="password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    placeholder="새 비밀번호 확인"
                    className="w-full rounded-xl border border-hairline bg-surface-soft px-4 py-2.5 text-sm text-ink focus:border-primary focus:outline-hidden"
                  />
                </div>
              </div>
            </>
          ) : (
            <div className="rounded-xl bg-surface-soft p-4 text-sm text-muted">
              카카오 로그인 계정은 닉네임과 프로필 사진만 변경할 수 있습니다.
              <br />
              이메일/비밀번호 변경은 카카오 계정 관리에서 해주세요.
            </div>
          )}

          <div className="flex gap-3 pt-2">
            <Link
              href="/profile"
              className="flex-1 rounded-xl border border-hairline bg-surface-soft py-2.5 text-center text-sm font-bold text-ink hover:bg-white transition-colors"
            >
              취소
            </Link>
            <button
              type="submit"
              disabled={isUploadingImage}
              className="flex-1 rounded-xl bg-primary py-2.5 text-sm font-bold text-white hover:bg-primary-active transition-colors disabled:opacity-70"
            >
              {isUploadingImage ? "이미지 업로드 중..." : "저장하기"}
            </button>
          </div>
        </form>
      </div>
    </AppShell>
  );
}
