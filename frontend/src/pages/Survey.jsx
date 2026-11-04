import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '../api.js'

const QUESTIONS = [
  ['manager', 'My manager'],
  ['pay', 'Pay and benefits'],
  ['growth', 'Career growth'],
  ['workLife', 'Work-life balance'],
  ['culture', 'Company culture'],
]

// The public exit survey (AAS-F-031): opened from the emailed link, no login needed.
export default function Survey() {
  const { token } = useParams()
  const [survey, setSurvey] = useState(null)
  const [answers, setAnswers] = useState({ comments: '' })
  const [closed, setClosed] = useState('')
  const [error, setError] = useState('')
  const [done, setDone] = useState(false)

  useEffect(() => {
    api(`/api/public/survey/${encodeURIComponent(token)}`, { anonymous: true })
      .then(setSurvey)
      .catch((err) => setClosed(err.status === 404 ? 'This survey link is not valid.' : err.message))
  }, [token])

  const complete = QUESTIONS.every(([key]) => answers[key])

  async function submit(e) {
    e.preventDefault()
    setError('')
    try {
      await api(`/api/public/survey/${encodeURIComponent(token)}`, { method: 'POST', body: answers, anonymous: true })
      setDone(true)
    } catch (err) {
      setError(err.message)
    }
  }

  let content
  if (done) {
    content = <p className="success">Thank you. Your feedback has been recorded.</p>
  } else if (closed) {
    content = <p className="error" role="alert">{closed}</p>
  } else if (survey) {
    content = (
      <form onSubmit={submit}>
        <p className="muted small">Rate each area from 1 (poor) to 5 (excellent). This link works once, until {survey.expiresOn}.</p>
        {QUESTIONS.map(([key, label]) => (
          <fieldset className="rating" key={key}>
            <legend>{label}</legend>
            {[1, 2, 3, 4, 5].map((n) => (
              <label key={n}>
                <input type="radio" name={key} value={n} checked={answers[key] === n}
                  onChange={() => setAnswers({ ...answers, [key]: n })} />
                {n}
              </label>
            ))}
          </fieldset>
        ))}
        <label>
          Anything else you would like to tell us? (optional)
          <textarea rows={4} maxLength={2000} value={answers.comments}
            onChange={(e) => setAnswers({ ...answers, comments: e.target.value })} />
        </label>
        {error && <p className="error" role="alert">{error}</p>}
        <button className="btn primary" disabled={!complete}>Submit</button>
      </form>
    )
  }

  return (
    <div className="login-page">
      <div className="login-card survey-card">
        <h1>Exit survey</h1>
        {survey && !done && <p>{survey.organizationName} would value your honest feedback.</p>}
        {content}
      </div>
    </div>
  )
}
