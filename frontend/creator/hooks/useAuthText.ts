import { useCallback } from 'react'
import authTranslations from '~/components/login/authTranslations.json'
import { useCreatorLocale, type CreatorLocale } from '~/hooks/useCreatorLocale'

const english: Record<string, string> = {
  '登录或注册': 'Sign in or sign up',
  '登录或创建账户': 'Sign in or create an account',
  '选择一种方式，开始你的创作': 'Choose how to continue your creative journey.',
  '验证后自动登录，新用户将创建账户': 'Verify to sign in. New here? We’ll create your account.',
  '欢迎回来，继续你的创作': 'Welcome back. Your next story awaits.',
  '使用手机号继续': 'Continue with phone',
  '使用邮箱继续': 'Continue with email',
  '正在加载登录方式…': 'Loading sign-in options…',
  '注册暂未开放，已有账户可使用密码登录': 'Registration is not available yet. Existing accounts can sign in with a password.',
  '已有账户？密码登录': 'Already have an account? Sign in with password',
  '返回其他登录方式': 'Back to sign-in options',
  '验证码登录': 'Sign in with a code',
  '微信扫码登录': 'Sign in with WeChat',
  '密码登录': 'Sign in with a password',
  '关闭': 'Close',
  '登录': 'Sign in',
  '登录中...': 'Signing in…',
  '免费注册': 'Create an account',
  '忘记密码？': 'Forgot password?',
  '获取验证码': 'Send code',
  '登录即表示同意': 'By signing in, you agree to the ',
  '《用户协议》': 'Terms of Service',
  '和': ' and ',
  '《隐私政策》': 'Privacy Policy',
  '，未注册的手机号或邮箱将自动创建账号': '. A new account will be created if your number or email is new here.',
  '，未注册的手机号将自动创建账号': '. A new account will be created if your number is new here.',
  '，未注册的邮箱将自动创建账号': '. A new account will be created if your email is new here.',
  '请输入手机号或邮箱': 'Phone number or email address',
  '请输入手机号': 'Phone number',
  '请输入邮箱': 'Email address',
  '请输入账号': 'Account',
  '手机号或邮箱': 'Phone number or email address',
  '手机号': 'Phone number',
  '邮箱': 'Email address',
  '账号': 'Account',
  '验证码': 'Verification code',
  '请输入验证码': 'Enter verification code',
  '请输入密码': 'Enter password',
  '扫码登录暂未开启': 'QR code sign-in is unavailable.',
  '微信登录二维码': 'WeChat sign-in QR code',
  '已扫码，登录处理中': 'Code scanned. Signing in…',
  '刷新中...': 'Refreshing…',
  '登录失败，点击刷新': 'Sign-in failed. Refresh code',
  '二维码已过期，点击刷新': 'Code expired. Refresh code',
  '获取微信二维码失败': 'Could not load the WeChat QR code.',
  '找回密码': 'Reset password',
  '找回密码表单': 'Password reset form',
  '找回密码进度': 'Password reset progress',
  '身份验证': 'Verify identity',
  '重置密码': 'Reset password',
  '完成': 'Done',
  '下一步': 'Continue',
  '设置新密码': 'New password',
  '确认新密码': 'Confirm new password',
  '输入新密码': 'Enter a new password',
  '请输入新密码': 'Enter a new password',
  '再次输入新密码': 'Re-enter your new password',
  '新密码已设置成功': 'Your password has been reset.',
  '去登录': 'Back to sign in',
  '密码须为 5-20 位，且同时包含大写字母、小写字母和数字': 'Use 5–20 characters, including an uppercase letter, a lowercase letter and a number.',
  '密码不能少于 5 位': 'Use at least 5 characters.',
  '密码不能超过 20 位': 'Use up to 20 characters.',
  '两次输入的密码不一致': 'The passwords do not match.',
  '新密码不得与旧密码相同': 'Choose a different password.',
  '验证码登录未开启，无法找回密码': 'Password recovery is unavailable because verification codes are disabled.',
  '验证码登录未开启': 'Verification code sign-in is unavailable.',
  '手机号格式不正确': 'Enter a valid phone number.',
  '邮箱格式不正确': 'Enter a valid email address.',
  '验证码格式不正确': 'Enter a valid verification code.',
  '登录成功': 'Signed in.',
  '登录失败': 'Sign-in failed.',
  '重置失败': 'Password reset failed.',
  '验证码已发送': 'Verification code sent.',
  '发送验证码失败': 'Could not send the verification code.',
  '用户名或密码错误': 'Incorrect account or password.',
  '账号或密码错误': 'Incorrect account or password.',
  '账号不能为空': 'Enter your phone number or email address.',
  '密码不能为空': 'Enter your password.',
  '账号已停用': 'This account has been disabled.',
  '账号不存在': 'Account not found.',
  '用户不存在': 'Account not found.',
  '验证码不能为空': 'Enter a verification code.',
  '验证码错误': 'Incorrect verification code.',
  '验证码已过期': 'The verification code has expired.',
  '验证码已使用或已过期': 'The verification code has been used or has expired.',
  '今日发送次数已达上限': 'Daily verification code limit reached.',
  '短信发送失败': 'Could not send the text message.',
  '邮件发送失败': 'Could not send the email.',
  '密码重置失败': 'Password reset failed.',
}

type Label = keyof typeof authTranslations.fr

const aliases: Record<string, Label> = {
  '，未注册的手机号将自动创建账号': '，未注册的手机号或邮箱将自动创建账号',
  '，未注册的邮箱将自动创建账号': '，未注册的手机号或邮箱将自动创建账号',
  '手机号或邮箱': '请输入手机号或邮箱',
  '手机号': '请输入手机号',
  '邮箱': '请输入邮箱',
  '账号': '请输入账号',
  '重置密码': '找回密码',
  '请输入新密码': '输入新密码',
  '用户名或密码错误': '账号或密码错误',
  '账号不能为空': '请输入手机号或邮箱',
  '密码不能为空': '请输入密码',
  '用户不存在': '账号不存在',
  '验证码不能为空': '请输入验证码',
  '密码重置失败': '重置失败',
}

function localizeAuthText(locale: CreatorLocale, text: string): string {
  if (locale === 'zh') return text
  const dictionary: Record<string, string> = locale === 'en' ? english : authTranslations[locale]
  const key = Object.hasOwn(dictionary, text) ? text : aliases[text]
  if (key && Object.hasOwn(dictionary, key)) return dictionary[key]
  const wait = /^发送过于频繁，请(\d+)秒后重试$/.exec(text)
  if (!wait) return english[text] ?? text
  const duration = formatAuthSeconds(locale, Number(wait[1]))
  return (locale === 'en' ? 'Try again in {time}.' : authTranslations[locale].retry).replace('{time}', duration)
}

export function formatAuthSeconds(locale: CreatorLocale, seconds: number) {
  return new Intl.NumberFormat(locale, {
    style: 'unit', unit: 'second', unitDisplay: 'short',
  }).format(seconds)
}

export function useAuthText() {
  const { locale } = useCreatorLocale()
  return useCallback((text: string) => localizeAuthText(locale, text), [locale])
}
