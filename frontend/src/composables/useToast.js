import { ref } from 'vue'

const toast = ref({ show: false, message: '', type: 'success' })
let timer = null

export function useToast() {
  const showToast = (message, type = 'success') => {
    clearTimeout(timer)
    toast.value = { show: true, message, type }
    timer = setTimeout(() => { toast.value.show = false }, 2500)
  }

  return { toast, showToast }
}
